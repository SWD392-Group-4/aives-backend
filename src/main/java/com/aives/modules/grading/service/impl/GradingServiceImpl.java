package com.aives.modules.grading.service.impl;

import com.aives.common.util.AuditTime;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.service.UserService;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.grading.dto.AttemptReviewResponse;
import com.aives.modules.grading.dto.CriteriaGradeResponse;
import com.aives.modules.grading.dto.GradeExchangeResponse;
import com.aives.modules.grading.dto.GradingAttemptResponse;
import com.aives.modules.grading.dto.QuestionGradeResponse;
import com.aives.modules.grading.dto.SaveQuestionGradeRequest;
import com.aives.modules.grading.entity.CriteriaGrade;
import com.aives.modules.grading.entity.QuestionGrade;
import com.aives.modules.grading.entity.GradeAppeal;
import com.aives.modules.grading.repository.CriteriaGradeRepository;
import com.aives.modules.grading.repository.GradeAppealRepository;
import com.aives.modules.grading.repository.GradingAttemptRepository;
import com.aives.modules.grading.repository.QuestionGradeRepository;
import com.aives.modules.grading.service.AttemptGradingService;
import com.aives.modules.grading.service.GradingService;
import com.aives.modules.vivaroom.entity.InterviewExchange;
import com.aives.modules.vivaroom.repository.InterviewExchangeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GradingServiceImpl implements GradingService {

    // BR-GRADE-002: diem chot lech qua muc nay so voi diem AI thi phai co ghi chu
    private static final BigDecimal NOTE_REQUIRED_DIFF = new BigDecimal("2.0");
    private static final int MIN_NOTE_LENGTH = 5;
    private static final BigDecimal TEN = BigDecimal.TEN;

    private final GradingAttemptRepository gradingAttemptRepository;
    private final VivaAttemptRepository attemptRepository;
    private final QuestionGradeRepository questionGradeRepository;
    private final CriteriaGradeRepository criteriaGradeRepository;
    private final RubricRepository rubricRepository;
    private final InterviewExchangeRepository exchangeRepository;
    private final GradeAppealRepository appealRepository;
    private final AttemptGradingService attemptGradingService;
    private final UserService userService;
    private final Clock clock;

    /* ========================= GIANG VIEN ========================= */

    @Override
    @Transactional(readOnly = true)
    public List<GradingAttemptResponse> listAttemptsForReview(UserPrincipal actor) {
        List<VivaAttempt> attempts = isAdmin(actor)
                ? gradingAttemptRepository.findForReview(AttemptStatus.COMPLETED)
                : gradingAttemptRepository.findForReviewByLecturer(AttemptStatus.COMPLETED, actor.getId());
        Set<String> studentIds = new HashSet<>();
        attempts.forEach(attempt -> studentIds.add(attempt.getStudentId()));
        Map<String, UserResponse> students = userService.getUsersByIds(studentIds);

        List<GradingAttemptResponse> result = new ArrayList<>();
        for (VivaAttempt attempt : attempts) {
            result.add(toAttemptResponse(attempt, students.get(attempt.getStudentId()), true));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptReviewResponse getReview(UserPrincipal actor, String attemptId) {
        VivaAttempt attempt = findOwnedAttempt(actor, attemptId);
        UserResponse student = userService.getUsersByIds(List.of(attempt.getStudentId())).get(attempt.getStudentId());
        return AttemptReviewResponse.builder()
                .attempt(toAttemptResponse(attempt, student, true))
                .questionGrades(toGradeResponses(attemptId, true))
                .build();
    }

    @Override
    @Transactional
    public QuestionGradeResponse saveQuestionGrade(UserPrincipal actor, String questionGradeId, BigDecimal finalScore,
                                                   String lecturerNote) {
        return saveQuestionGrade(actor, questionGradeId, SaveQuestionGradeRequest.builder()
                .finalScore(finalScore)
                .lecturerNote(lecturerNote)
                .build());
    }

    @Override
    @Transactional
    public QuestionGradeResponse saveQuestionGrade(UserPrincipal actor, String questionGradeId,
                                                   SaveQuestionGradeRequest request) {
        QuestionGrade grade = questionGradeRepository.findWithAttemptById(questionGradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_GRADE_NOT_FOUND));
        VivaAttempt attempt = grade.getAttempt();
        requireOwner(actor, attempt.getExam());
        requireEditable(attempt);

        BigDecimal maxScore = maxScoreOf(grade);
        BigDecimal score = request.getFinalScore().setScale(2, RoundingMode.HALF_UP);
        if (score.signum() < 0 || score.compareTo(maxScore) > 0) {
            throw new BusinessException(ErrorCode.GRADE_SCORE_INVALID,
                    "Điểm phải nằm trong khoảng 0 đến " + maxScore.stripTrailingZeros().toPlainString());
        }
        String note = request.getLecturerNote() == null || request.getLecturerNote().isBlank()
                ? null
                : request.getLecturerNote().trim();
        if (grade.getAiScore() != null
                && score.subtract(grade.getAiScore()).abs().compareTo(NOTE_REQUIRED_DIFF) > 0
                && (note == null || note.length() < MIN_NOTE_LENGTH)) {
            throw new BusinessException(ErrorCode.GRADE_NOTE_REQUIRED);
        }

        grade.setFinalScore(score);
        grade.setLecturerNote(note);
        grade.setStatus(AttemptGradingService.GRADE_APPROVED);
        grade.setGradedBy(actor.getId());
        grade.setGradedAt(now());
        questionGradeRepository.saveAndFlush(grade);

        // Cap nhat diem tung tieu chi neu giang vien co gui
        if (request.getCriteriaScores() != null && !request.getCriteriaScores().isEmpty()) {
            List<CriteriaGrade> criteriaGrades = criteriaGradeRepository.findByQuestionGradeIds(List.of(questionGradeId));
            for (SaveQuestionGradeRequest.CriterionScoreInput input : request.getCriteriaScores()) {
                if (input.getFinalScore() == null) {
                    continue;
                }
                for (CriteriaGrade cg : criteriaGrades) {
                    boolean match = (input.getCriteriaGradeId() != null && input.getCriteriaGradeId().equals(cg.getId()))
                            || (input.getRubricCriteriaId() != null && input.getRubricCriteriaId().equals(cg.getRubricCriterion().getId()));
                    if (match) {
                        cg.setFinalScore(input.getFinalScore().setScale(2, RoundingMode.HALF_UP));
                        criteriaGradeRepository.save(cg);
                        break;
                    }
                }
            }
            criteriaGradeRepository.flush();
        }

        // Tong diem chot chi co khi moi cau da duoc chot
        attemptGradingService.refreshTotals(attempt, questionGradeRepository.findByAttemptInOrder(attempt.getId()));
        attemptRepository.save(attempt);

        return toGradeResponses(attempt.getId(), true).stream()
                .filter(item -> item.getId().equals(questionGradeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_GRADE_NOT_FOUND));
    }

    @Override
    @Transactional
    public GradingAttemptResponse publish(UserPrincipal actor, String attemptId) {
        VivaAttempt attempt = findOwnedAttempt(actor, attemptId);
        requireEditable(attempt);

        List<QuestionGrade> grades = questionGradeRepository.findByAttemptInOrder(attemptId);
        long notApproved = grades.stream()
                .filter(grade -> !AttemptGradingService.GRADE_APPROVED.equals(grade.getStatus()))
                .count();
        if (grades.isEmpty() || notApproved > 0) {
            throw new BusinessException(ErrorCode.GRADES_NOT_APPROVED, grades.isEmpty()
                    ? "Lượt thi chưa có điểm câu hỏi nào để công bố"
                    : "Còn " + notApproved + " câu chưa được giảng viên duyệt điểm. Vui lòng duyệt tất cả trước khi công bố");
        }
        attemptGradingService.refreshTotals(attempt, grades);
        attempt.setResultStatus(AttemptGradingService.PUBLISHED);
        attempt.setPublishedAt(now());
        attemptRepository.save(attempt);

        UserResponse student = userService.getUsersByIds(List.of(attempt.getStudentId())).get(attempt.getStudentId());
        return toAttemptResponse(attempt, student, true);
    }

    @Override
    @Transactional
    public GradingAttemptResponse requestRegrade(UserPrincipal actor, String attemptId) {
        VivaAttempt attempt = findOwnedAttempt(actor, attemptId);
        requireEditable(attempt);

        List<QuestionGrade> toDelete = questionGradeRepository.findByAttemptInOrder(attemptId).stream()
                .filter(grade -> !AttemptGradingService.GRADE_APPROVED.equals(grade.getStatus()))
                .toList();
        if (!toDelete.isEmpty()) {
            List<String> gradeIds = toDelete.stream().map(QuestionGrade::getId).toList();
            criteriaGradeRepository.deleteAll(criteriaGradeRepository.findByQuestionGradeIds(gradeIds));
            questionGradeRepository.deleteAll(toDelete);
            questionGradeRepository.flush();
        }
        // Ve NONE de bo cham ngam nhan lai luot thi nay
        attempt.setResultStatus(AttemptGradingService.NONE);
        attempt.setTotalAiScore(null);
        attempt.setTotalFinalScore(null);
        attemptRepository.save(attempt);

        UserResponse student = userService.getUsersByIds(List.of(attempt.getStudentId())).get(attempt.getStudentId());
        return toAttemptResponse(attempt, student, true);
    }

    /* ========================= SINH VIEN ========================= */

    @Override
    @Transactional(readOnly = true)
    public List<GradingAttemptResponse> listMyAttempts(String studentId) {
        List<GradingAttemptResponse> result = new ArrayList<>();
        for (VivaAttempt attempt : gradingAttemptRepository.findCompletedOfStudent(studentId, AttemptStatus.COMPLETED)) {
            // Chua cong bo thi sinh vien khong thay diem (BR-GRADE-003)
            result.add(toAttemptResponse(attempt, null, isPublished(attempt)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptReviewResponse getMyResult(String studentId, String attemptId) {
        // Luot thi cua nguoi khac cung bao "khong tim thay"
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId)
                .filter(found -> found.getStudentId().equals(studentId))
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
        if (!isPublished(attempt)) {
            throw new BusinessException(ErrorCode.RESULT_NOT_PUBLISHED);
        }
        return AttemptReviewResponse.builder()
                .attempt(toAttemptResponse(attempt, null, true))
                .questionGrades(toGradeResponses(attemptId, false))
                .build();
    }

    /* ========================= NOI BO ========================= */

    private VivaAttempt findOwnedAttempt(UserPrincipal actor, String attemptId) {
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
        requireOwner(actor, attempt.getExam());
        return attempt;
    }

    private void requireOwner(UserPrincipal actor, VivaExam exam) {
        if (!isAdmin(actor) && !exam.getLecturerId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn chỉ được thẩm định lượt thi của phiên thi do mình tạo");
        }
    }

    /** Dang cham thi cho xong; da cong bo thi khong sua nua (sua qua phuc khao). */
    private void requireEditable(VivaAttempt attempt) {
        if (AttemptGradingService.GRADING.equals(attempt.getResultStatus())) {
            throw new BusinessException(ErrorCode.GRADING_IN_PROGRESS);
        }
        if (isPublished(attempt)) {
            throw new BusinessException(ErrorCode.RESULT_ALREADY_PUBLISHED);
        }
    }

    private boolean isPublished(VivaAttempt attempt) {
        return AttemptGradingService.PUBLISHED.equals(attempt.getResultStatus());
    }

    private boolean isAdmin(UserPrincipal actor) {
        return actor.getRole() == RoleName.ADMIN;
    }

    private BigDecimal maxScoreOf(QuestionGrade grade) {
        Question question = grade.getExamQuestion().getQuestion();
        if (question.getRubric() != null) {
            return question.getRubric().getMaxScore() != null ? question.getRubric().getMaxScore() : TEN;
        }
        VivaExam exam = grade.getAttempt() != null ? grade.getAttempt().getExam() : null;
        String courseId = exam != null ? exam.getCourseId() : null;
        if (!StringUtils.hasText(courseId) && question.getChapter() != null && question.getChapter().getCourse() != null) {
            courseId = question.getChapter().getCourse().getId();
        }
        if (StringUtils.hasText(courseId)) {
            return rubricRepository.findByCourse_Id(courseId)
                    .map(r -> r.getMaxScore() != null ? r.getMaxScore() : TEN)
                    .orElse(TEN);
        }
        return TEN;
    }

    private GradingAttemptResponse toAttemptResponse(VivaAttempt attempt, UserResponse student, boolean withScores) {
        VivaExam exam = attempt.getExam();
        return GradingAttemptResponse.builder()
                .attemptId(attempt.getId())
                .examId(exam.getId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .attemptNo(attempt.getAttemptNo())
                .maxAttempts(exam.getMaxAttempts())
                .status(attempt.getStatus().name())
                .resultStatus(attempt.getResultStatus())
                .totalAiScore(withScores ? attempt.getTotalAiScore() : null)
                .totalFinalScore(withScores ? attempt.getTotalFinalScore() : null)
                .startedAt(toInstant(attempt.getStartedAt()))
                .deadlineAt(toInstant(attempt.getDeadlineAt()))
                .completedAt(toInstant(attempt.getCompletedAt()))
                .publishedAt(toInstant(attempt.getPublishedAt()))
                .serverTime(clock.instant())
                .remainingSeconds(0)
                .studentId(attempt.getStudentId())
                .studentName(student == null ? null : student.getFullName())
                .studentEmail(student == null ? null : student.getEmail())
                .build();
    }

    /** forLecturer = false (sinh vien xem): giau ly do quyet dinh cua AI trong tung luot hoi - dap. */
    private List<QuestionGradeResponse> toGradeResponses(String attemptId, boolean forLecturer) {
        List<QuestionGrade> grades = questionGradeRepository.findByAttemptInOrder(attemptId);
        if (grades.isEmpty()) {
            return List.of();
        }
        Map<String, List<CriteriaGradeResponse>> criteriaByGrade = new HashMap<>();
        List<String> gradeIds = grades.stream().map(QuestionGrade::getId).toList();
        for (CriteriaGrade criteriaGrade : criteriaGradeRepository.findByQuestionGradeIds(gradeIds)) {
            String gradeId = criteriaGrade.getQuestionGrade().getId();
            criteriaByGrade.computeIfAbsent(gradeId, key -> new ArrayList<>()).add(CriteriaGradeResponse.builder()
                    .id(criteriaGrade.getId())
                    .questionGradeId(gradeId)
                    .rubricCriteriaId(criteriaGrade.getRubricCriterion().getId())
                    .criterionName(criteriaGrade.getRubricCriterion().getName())
                    .weightPercent(criteriaGrade.getRubricCriterion().getWeightPercent())
                    .aiScore(criteriaGrade.getAiScore())
                    .finalScore(criteriaGrade.getFinalScore())
                    .evidenceQuote(criteriaGrade.getEvidenceQuote())
                    .rationale(criteriaGrade.getRationale())
                    .build());
        }

        Map<String, List<GradeExchangeResponse>> exchangesByQuestion = new HashMap<>();
        for (InterviewExchange exchange : exchangeRepository.findByAttemptInOrder(attemptId)) {
            String examQuestionId = exchange.getExamQuestion().getId();
            exchangesByQuestion.computeIfAbsent(examQuestionId, key -> new ArrayList<>()).add(GradeExchangeResponse.builder()
                    .id(exchange.getId())
                    .attemptId(attemptId)
                    .questionId(examQuestionId)
                    .parentExchangeId(exchange.getParentExchange() == null ? null : exchange.getParentExchange().getId())
                    .depth(exchange.getDepth())
                    .questionText(exchange.getQuestionText())
                    .transcript(exchange.getTranscript())
                    .followUpReason(exchange.getFollowUpReason())
                    .aiDecision(exchange.getAiDecision())
                    .aiDecisionReason(forLecturer ? exchange.getAiDecisionReason() : null)
                    .answerEndedAt(toInstant(exchange.getAnswerEndedAt()))
                    .latencyMs(exchange.getLatencyMs())
                    .createdAt(AuditTime.toInstant(exchange.getCreatedAt()))
                    .build());
        }

        // Don phuc khao moi nhat cua tung cau (danh sach da sap moi nhat truoc)
        Map<String, QuestionGradeResponse.AppealSummary> latestAppeals = new HashMap<>();
        for (GradeAppeal appeal : appealRepository.findByAttempt(attemptId)) {
            latestAppeals.putIfAbsent(appeal.getQuestionGrade().getId(), QuestionGradeResponse.AppealSummary.builder()
                    .id(appeal.getId())
                    .status(appeal.getStatus())
                    .reason(appeal.getReason())
                    .response(appeal.getResponse())
                    .scoreBefore(appeal.getScoreBefore())
                    .scoreAfter(appeal.getScoreAfter())
                    .createdAt(AuditTime.toInstant(appeal.getCreatedAt()))
                    .resolvedAt(toInstant(appeal.getResolvedAt()))
                    .build());
        }

        Set<String> graderIds = new HashSet<>();
        grades.forEach(grade -> {
            if (grade.getGradedBy() != null) {
                graderIds.add(grade.getGradedBy());
            }
        });
        Map<String, String> graderNames = userService.getFullNamesByIds(graderIds);

        List<QuestionGradeResponse> result = new ArrayList<>();
        for (QuestionGrade grade : grades) {
            Question question = grade.getExamQuestion().getQuestion();
            result.add(QuestionGradeResponse.builder()
                    .id(grade.getId())
                    .attemptId(attemptId)
                    .questionId(grade.getExamQuestion().getId())
                    .questionText(question.getContent())
                    .bloomLevel(question.getBloomLevel())
                    .weight(grade.getExamQuestion().getWeight())
                    .maxScore(maxScoreOf(grade))
                    .aiScore(grade.getAiScore())
                    .finalScore(grade.getFinalScore())
                    .aiStrengths(grade.getAiStrengths())
                    .aiWeaknesses(grade.getAiWeaknesses())
                    .aiFeedback(grade.getAiFeedback())
                    .lecturerNote(grade.getLecturerNote())
                    .status(grade.getStatus())
                    .gradedBy(grade.getGradedBy())
                    .gradedByName(grade.getGradedBy() == null ? null : graderNames.get(grade.getGradedBy()))
                    .gradedAt(toInstant(grade.getGradedAt()))
                    .criteriaGrades(criteriaByGrade.getOrDefault(grade.getId(), List.of()))
                    .exchanges(exchangesByQuestion.getOrDefault(grade.getExamQuestion().getId(), List.of()))
                    .appeal(latestAppeals.get(grade.getId()))
                    .build());
        }
        return result;
    }

    private Instant toInstant(LocalDateTime utcDateTime) {
        return utcDateTime == null ? null : utcDateTime.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
