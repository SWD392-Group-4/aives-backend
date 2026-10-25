package com.aives.modules.grading.service;

import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.entity.VivaExamQuestion;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamQuestionRepository;
import com.aives.modules.grading.entity.CriteriaGrade;
import com.aives.modules.grading.entity.QuestionGrade;
import com.aives.modules.grading.repository.CriteriaGradeRepository;
import com.aives.modules.grading.repository.GradingAttemptRepository;
import com.aives.modules.grading.repository.QuestionGradeRepository;
import com.aives.modules.grading.service.AnswerGrader.Criterion;
import com.aives.modules.grading.service.AnswerGrader.CriterionScore;
import com.aives.modules.grading.service.AnswerGrader.Input;
import com.aives.modules.grading.service.AnswerGrader.Result;
import com.aives.modules.grading.service.AnswerGrader.Turn;
import com.aives.modules.vivaroom.entity.InterviewExchange;
import com.aives.modules.vivaroom.repository.InterviewExchangeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * AI cham 1 luot thi sau khi sinh vien thi xong (giai doan 3 trong tai lieu kien truc).
 *
 * result_status cua luot thi:  NONE -> GRADING -> PENDING_REVIEW (cham xong, cho giang vien tham dinh)
 *                                              -> GRADING_FAILED (co cau AI khong cham duoc, giang vien cham tay hoac bam cham lai)
 *
 * Chia 3 buoc de KHONG giu transaction DB trong luc cho AI: nhan viec + doc du lieu -> goi AI tung cau -> luu diem.
 * Cau hoi da co diem (vd giang vien da chot truoc khi bam "cham lai") thi giu nguyen, khong cham lai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptGradingService {

    public static final String NONE = "NONE";
    public static final String GRADING = "GRADING";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String GRADING_FAILED = "GRADING_FAILED";
    public static final String PUBLISHED = "PUBLISHED";

    public static final String GRADE_AI_DRAFT = "AI_DRAFT";
    public static final String GRADE_APPROVED = "APPROVED";
    public static final String GRADE_FAILED = "FAILED";

    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final String SKIPPED = "SKIPPED";

    private final GradingAttemptRepository gradingAttemptRepository;
    private final VivaAttemptRepository attemptRepository;
    private final VivaExamQuestionRepository examQuestionRepository;
    private final InterviewExchangeRepository exchangeRepository;
    private final RubricCriterionRepository criterionRepository;
    private final RubricRepository rubricRepository;
    private final QuestionGradeRepository questionGradeRepository;
    private final CriteriaGradeRepository criteriaGradeRepository;
    private final AnswerGrader answerGrader;
    private final RandomIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Chot cac luot thi da qua han ma con IN_PROGRESS (sinh vien tat trinh duyet giua chung). Tra ve so luot da chot. */
    public int closeOverdueAttempts() {
        Integer closed = transactionTemplate.execute(status -> {
            List<VivaAttempt> overdue = gradingAttemptRepository.findOverdue(AttemptStatus.IN_PROGRESS, now());
            for (VivaAttempt attempt : overdue) {
                attempt.setStatus(AttemptStatus.COMPLETED);
                attempt.setCompletedAt(attempt.getDeadlineAt());
                attemptRepository.save(attempt);
            }
            return overdue.size();
        });
        return closed == null ? 0 : closed;
    }

    /** Cham toi da maxAttempts luot thi dang cho cham. Tra ve so luot da cham. */
    public int gradeWaitingAttempts(int maxAttempts) {
        List<String> ids = gradingAttemptRepository.findIdsWaitingForGrading(
                AttemptStatus.COMPLETED, PageRequest.of(0, Math.max(maxAttempts, 1)));
        int graded = 0;
        for (String attemptId : ids) {
            if (gradeAttempt(attemptId)) {
                graded++;
            }
        }
        return graded;
    }

    /** Backend bi tat giua luc cham thi luot thi ket o GRADING: dua ve NONE de duoc cham lai. */
    public int resetInterruptedGrading() {
        Integer reset = transactionTemplate.execute(status -> gradingAttemptRepository.resetInterruptedGrading(now()));
        return reset == null ? 0 : reset;
    }

    /**
     * Cham 1 luot thi dang o trang thai NONE.
     *
     * @return false neu luot thi khong o trang thai NONE (da co luong khac cham)
     */
    public boolean gradeAttempt(String attemptId) {
        // Buoc 1: nhan viec (chi 1 luong nhan duoc) va doc du lieu can cham
        List<Job> jobs = transactionTemplate.execute(status -> {
            if (gradingAttemptRepository.changeResultStatus(attemptId, NONE, GRADING, now()) == 0) {
                return null;
            }
            return prepareJobs(attemptId);
        });
        if (jobs == null) {
            return false;
        }

        try {
            // Buoc 2: goi AI tung cau, khong nam trong transaction
            List<Graded> gradedList = new ArrayList<>();
            for (Job job : jobs) {
                Result result = job.answered()
                        ? answerGrader.grade(job.input())
                        : answerGrader.zero(job.input(), "Sinh viên không trả lời câu hỏi này");
                gradedList.add(new Graded(job, result));
            }
            // Buoc 3: luu diem va cap nhat luot thi
            transactionTemplate.executeWithoutResult(status -> saveGrades(attemptId, gradedList));
        } catch (RuntimeException ex) {
            log.error("Cham luot thi {} bi loi", attemptId, ex);
            transactionTemplate.executeWithoutResult(status ->
                    gradingAttemptRepository.changeResultStatus(attemptId, GRADING, GRADING_FAILED, now()));
        }
        return true;
    }

    /** Tinh lai tong diem AI va tong diem chot cua luot thi tu diem tung cau. Goi trong transaction. */
    public void refreshTotals(VivaAttempt attempt, List<QuestionGrade> grades) {
        attempt.setTotalAiScore(weightedTotal(attempt, grades, false));
        attempt.setTotalFinalScore(weightedTotal(attempt, grades, true));
    }

    /* ========================= NOI BO ========================= */

    /** 1 cau hoi can cham. answered = false: sinh vien khong tra loi gi, cho 0 diem ma khong hoi AI. */
    private record Job(String examQuestionId, Input input, boolean answered) {
    }

    private record Graded(Job job, Result result) {
    }

    private List<Job> prepareJobs(String attemptId) {
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId).orElseThrow();
        List<VivaExamQuestion> examQuestions = examQuestionRepository.findWithQuestionByExamId(attempt.getExam().getId());
        List<InterviewExchange> exchanges = exchangeRepository.findByAttemptInOrder(attemptId);

        Set<String> alreadyGraded = new HashSet<>();
        for (QuestionGrade grade : questionGradeRepository.findByAttemptInOrder(attemptId)) {
            alreadyGraded.add(grade.getExamQuestion().getId());
        }

        String examCourseId = attempt.getExam() != null ? attempt.getExam().getCourseId() : null;
        Rubric examCourseRubric = StringUtils.hasText(examCourseId)
                ? rubricRepository.findByCourse_Id(examCourseId).orElse(null)
                : null;

        List<Job> jobs = new ArrayList<>();
        for (VivaExamQuestion examQuestion : examQuestions) {
            if (alreadyGraded.contains(examQuestion.getId())) {
                continue;
            }
            Question question = examQuestion.getQuestion();
            List<Criterion> criteria = new ArrayList<>();
            BigDecimal maxScore = TEN;
            if (question.getRubric() != null) {
                maxScore = question.getRubric().getMaxScore() != null ? question.getRubric().getMaxScore() : TEN;
                for (RubricCriterion item : criterionRepository.findByRubric_IdOrderByOrderNoAsc(question.getRubric().getId())) {
                    criteria.add(new Criterion(item.getId(), item.getName(), item.getDescription(), item.getWeightPercent()));
                }
            } else {
                // Ke thua tieu chi tu Rubric mon hoc (CourseRubric)
                Rubric courseRubric = examCourseRubric;
                if (courseRubric == null && question.getChapter() != null && question.getChapter().getCourse() != null) {
                    courseRubric = rubricRepository.findByCourse_Id(question.getChapter().getCourse().getId()).orElse(null);
                }
                if (courseRubric != null) {
                    maxScore = courseRubric.getMaxScore() != null ? courseRubric.getMaxScore() : TEN;
                    for (RubricCriterion item : criterionRepository.findByRubric_IdOrderByOrderNoAsc(courseRubric.getId())) {
                        criteria.add(new Criterion(item.getId(), item.getName(), item.getDescription(), item.getWeightPercent()));
                    }
                }
            }
            List<Turn> turns = new ArrayList<>();
            boolean answered = false;
            for (InterviewExchange exchange : exchanges) {
                if (!exchange.getExamQuestion().getId().equals(examQuestion.getId())) {
                    continue;
                }
                String transcript = exchange.getTranscript() == null ? "" : exchange.getTranscript().trim();
                if (!transcript.isEmpty() && !SKIPPED.equals(exchange.getAiDecision())) {
                    answered = true;
                }
                turns.add(new Turn(exchange.getQuestionText(), transcript));
            }
            if (turns.isEmpty()) {
                // Sinh vien chua toi cau nay (het gio hoac nop bai som)
                turns.add(new Turn(question.getContent(), ""));
            }
            jobs.add(new Job(examQuestion.getId(),
                    new Input(question.getModelAnswer(), question.getExpectedKeywords(), maxScore, criteria, turns),
                    answered));
        }
        return jobs;
    }

    private void saveGrades(String attemptId, List<Graded> gradedList) {
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId).orElseThrow();
        Map<String, VivaExamQuestion> examQuestions = new HashMap<>();
        for (VivaExamQuestion examQuestion : examQuestionRepository.findWithQuestionByExamId(attempt.getExam().getId())) {
            examQuestions.put(examQuestion.getId(), examQuestion);
        }

        for (Graded graded : gradedList) {
            Result result = graded.result();
            QuestionGrade grade = QuestionGrade.builder()
                    .attempt(attempt)
                    .examQuestion(examQuestions.get(graded.job().examQuestionId()))
                    .aiScore(result.failed() ? null : result.totalScore())
                    .aiStrengths(result.strengths())
                    .aiWeaknesses(result.weaknesses())
                    .aiFeedback(result.failed() ? result.error() : result.feedback())
                    .status(result.failed() ? GRADE_FAILED : GRADE_AI_DRAFT)
                    .build();
            grade.setId(idGenerator.next("QG"));
            questionGradeRepository.save(grade);

            for (CriterionScore score : result.criteria()) {
                CriteriaGrade criteriaGrade = CriteriaGrade.builder()
                        .questionGrade(grade)
                        .rubricCriterion(criterionRepository.getReferenceById(score.criterionId()))
                        .aiScore(score.score())
                        .evidenceQuote(score.evidenceQuote())
                        .rationale(score.rationale())
                        .build();
                criteriaGrade.setId(idGenerator.next("CG"));
                criteriaGradeRepository.save(criteriaGrade);
            }
        }
        questionGradeRepository.flush();

        List<QuestionGrade> allGrades = questionGradeRepository.findByAttemptInOrder(attemptId);
        boolean anyFailed = allGrades.stream().anyMatch(grade -> GRADE_FAILED.equals(grade.getStatus()));
        refreshTotals(attempt, allGrades);
        attempt.setResultStatus(anyFailed ? GRADING_FAILED : PENDING_REVIEW);
        attemptRepository.save(attempt);
        log.info("Da cham luot thi {}: {} cau, ket qua {}", attemptId, allGrades.size(), attempt.getResultStatus());
    }

    /**
     * Tong diem tren thang 10 = trung binh co trong so cua (diem cau / thang diem rubric x 10).
     * useFinal = true: dung diem giang vien chot, tra null neu con cau chua chot.
     * useFinal = false: dung diem AI, cau AI khong cham duoc tinh 0.
     */
    private BigDecimal weightedTotal(VivaAttempt attempt, List<QuestionGrade> grades, boolean useFinal) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        VivaExam exam = attempt != null ? attempt.getExam() : null;
        for (QuestionGrade grade : grades) {
            BigDecimal score = useFinal ? grade.getFinalScore() : grade.getAiScore();
            if (score == null) {
                if (useFinal) {
                    return null;
                }
                score = BigDecimal.ZERO;
            }
            VivaExamQuestion examQuestion = grade.getExamQuestion();
            BigDecimal maxScore = resolveMaxScore(examQuestion, exam);
            BigDecimal onTen = score.multiply(TEN).divide(maxScore, 6, RoundingMode.HALF_UP);
            sum = sum.add(onTen.multiply(examQuestion.getWeight()));
            weightSum = weightSum.add(examQuestion.getWeight());
        }
        if (weightSum.signum() == 0) {
            return null;
        }
        return sum.divide(weightSum, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveMaxScore(VivaExamQuestion examQuestion, VivaExam exam) {
        if (examQuestion.getQuestion().getRubric() != null) {
            return examQuestion.getQuestion().getRubric().getMaxScore() != null
                    ? examQuestion.getQuestion().getRubric().getMaxScore()
                    : TEN;
        }
        String courseId = exam != null ? exam.getCourseId() : null;
        if (!StringUtils.hasText(courseId) && examQuestion.getQuestion().getChapter() != null
                && examQuestion.getQuestion().getChapter().getCourse() != null) {
            courseId = examQuestion.getQuestion().getChapter().getCourse().getId();
        }
        if (StringUtils.hasText(courseId)) {
            return rubricRepository.findByCourse_Id(courseId)
                    .map(r -> r.getMaxScore() != null ? r.getMaxScore() : TEN)
                    .orElse(TEN);
        }
        return TEN;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
