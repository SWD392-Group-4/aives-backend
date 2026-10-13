package com.aives.modules.vivaroom.service.impl;

import com.aives.common.util.AuditTime;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.RandomIdGenerator;
import com.aives.integration.ai.LlmClient;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.exam.dto.ExamAttemptResponse;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.entity.VivaExamQuestion;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamQuestionRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import com.aives.modules.exam.service.ExamAttemptService;
import com.aives.modules.vivaroom.dto.InterviewExchangeResponse;
import com.aives.modules.vivaroom.dto.InterviewStateResponse;
import com.aives.modules.vivaroom.entity.InterviewExchange;
import com.aives.modules.vivaroom.enums.InterviewStatus;
import com.aives.modules.vivaroom.repository.InterviewExchangeRepository;
import com.aives.modules.vivaroom.service.FollowUpDecisionService;
import com.aives.modules.vivaroom.service.FollowUpDecisionService.Context;
import com.aives.modules.vivaroom.service.FollowUpDecisionService.Criterion;
import com.aives.modules.vivaroom.service.FollowUpDecisionService.Decision;
import com.aives.modules.vivaroom.service.FollowUpDecisionService.Turn;
import com.aives.modules.vivaroom.service.VivaInterviewService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Trang thai phong van KHONG luu rieng ma suy ra tu bang interview_exchanges:
 *   - luot hoi moi nhat chua co transcript va chua co ai_decision  -> dang cho sinh vien tra loi
 *   - da co transcript nhung chua co ai_decision                    -> AI dang quyet dinh
 *   - luot hoi moi nhat da co ai_decision va khong con cau nao nua  -> da xong
 *
 * Moi lan tra loi chia 3 buoc de KHONG giu transaction DB trong luc cho AI (co the mat vai giay):
 *   1. (transaction) kiem tra + ghi cau tra loi    2. goi AI    3. (transaction) ghi quyet dinh + tao cau tiep theo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VivaInterviewServiceImpl implements VivaInterviewService {

    private static final String EXCHANGE_ID_PREFIX = "IE";
    private static final String EXAM_QUESTION_ID_PREFIX = "EQ";
    private static final String SKIPPED = "SKIPPED";
    private static final String QUESTION_APPROVED = "APPROVED";

    // AI qua lau khong tra quyet dinh (backend bi tat giua chung...) thi lan vao lai se tu chuyen cau tiep theo
    private static final Duration PROCESSING_STALE_AFTER = Duration.ofSeconds(90);

    private final VivaAttemptRepository attemptRepository;
    private final VivaExamRepository examRepository;
    private final VivaExamQuestionRepository examQuestionRepository;
    private final InterviewExchangeRepository exchangeRepository;
    private final QuestionRepository questionRepository;
    private final RubricCriterionRepository criterionRepository;
    private final ExamAttemptService examAttemptService;
    private final FollowUpDecisionService followUpDecisionService;
    private final LlmClient llmClient;
    private final RandomIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // Phien thi chua duoc gan cau hoi thi tu lay bay nhieu cau APPROVED trong ngan hang (giai phap tam)
    @Value("${app.viva.default-question-count:3}")
    private int defaultQuestionCount;

    /* ========================= BAT DAU / VAO LAI ========================= */

    @Override
    public InterviewStateResponse start(String attemptId, String studentId) {
        // Kiem tra dung chu luot thi, dong thoi chot trang thai neu da het gio
        ExamAttemptResponse attempt = examAttemptService.getAttempt(attemptId, studentId);
        if (attempt.getStatus() == AttemptStatus.COMPLETED) {
            return completedState(false);
        }
        assignDefaultQuestionsIfEmpty(attempt.getExamId());

        Decision recovery = Decision.fallbackNext("AI không trả quyết định kịp nên hệ thống tự chuyển câu tiếp theo");
        return transactionTemplate.execute(status -> {
            Snapshot snapshot = load(attemptId);
            if (snapshot.questions().isEmpty()) {
                throw new BusinessException(ErrorCode.INTERVIEW_NO_QUESTIONS);
            }
            if (snapshot.exchanges().isEmpty()) {
                createExchange(snapshot.attempt(), snapshot.questions().get(0), null, null);
                return buildState(load(attemptId), false);
            }
            InterviewExchange latest = snapshot.latest();
            if (isProcessing(latest)
                    && latest.getAnswerEndedAt() != null
                    && latest.getAnswerEndedAt().plus(PROCESSING_STALE_AFTER).isBefore(now())) {
                return applyDecision(snapshot, latest, recovery, null, studentId);
            }
            return buildState(snapshot, false);
        });
    }

    /* ========================= TRA LOI ========================= */

    @Override
    public InterviewStateResponse answer(String attemptId, String studentId, String exchangeId, String transcript) {
        long startedAt = System.nanoTime();
        requireRunning(attemptId, studentId);
        String answerText = transcript == null ? "" : transcript.trim();

        // Buoc 1: ghi cau tra loi va gom du lieu cho AI
        Pending pending = transactionTemplate.execute(status -> {
            Snapshot snapshot = load(attemptId);
            InterviewExchange current = requireCurrent(snapshot, exchangeId);
            VivaExam exam = snapshot.attempt().getExam();
            int remainingFollowUps = exam.getMaxFollowUpPerQuestion() - current.getDepth();

            Pending result;
            if (answerText.isEmpty()) {
                result = new Pending(Decision.next("Sinh viên không trả lời"), null);
            } else if (remainingFollowUps <= 0) {
                // BR-VIVA-001: da cham tran so cau hoi xoay thi khong hoi AI nua
                result = new Pending(Decision.next("Đã hỏi đủ số câu hỏi xoáy tối đa của câu này"), null);
            } else {
                result = new Pending(null, buildContext(snapshot, current, answerText, remainingFollowUps));
            }

            // Ghi sau cung: lenh nay xoa cache cua transaction nen cac entity phia tren khong dung lai duoc nua
            if (exchangeRepository.saveAnswerIfUnanswered(exchangeId, answerText, now()) == 0) {
                throw new BusinessException(ErrorCode.INTERVIEW_EXCHANGE_NOT_CURRENT);
            }
            return result;
        });

        // Buoc 2: hoi AI, khong nam trong transaction
        Decision decision = pending.decision() != null
                ? pending.decision()
                : followUpDecisionService.decide(pending.context());

        // Buoc 3: ghi quyet dinh va tao cau tiep theo
        int latencyMs = (int) Math.min((System.nanoTime() - startedAt) / 1_000_000, Integer.MAX_VALUE);
        return transactionTemplate.execute(status -> {
            Snapshot snapshot = load(attemptId);
            InterviewExchange answered = snapshot.exchanges().stream()
                    .filter(exchange -> exchange.getId().equals(exchangeId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_EXCHANGE_NOT_CURRENT));
            if (answered.getAiDecision() != null) {
                // Da co noi khac ghi quyet dinh (vd sinh vien tai lai trang sau khi cho qua lau)
                return buildState(snapshot, false);
            }
            return applyDecision(snapshot, answered, decision, latencyMs, studentId);
        });
    }

    /* ========================= BO QUA ========================= */

    @Override
    public InterviewStateResponse skip(String attemptId, String studentId, String exchangeId) {
        requireRunning(attemptId, studentId);
        return transactionTemplate.execute(status -> {
            Snapshot snapshot = load(attemptId);
            InterviewExchange current = requireCurrent(snapshot, exchangeId);
            Decision skipped = new Decision(SKIPPED, null, null, "Sinh viên bỏ qua câu hỏi", false);
            return applyDecision(snapshot, current, skipped, null, studentId);
        });
    }

    /* ========================= GIANG VIEN XEM LAI ========================= */

    @Override
    public List<InterviewExchangeResponse> listExchanges(String attemptId, UserPrincipal actor) {
        return transactionTemplate.execute(status -> {
            VivaAttempt attempt = attemptRepository.findWithExamById(attemptId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
            if (actor.getRole() != RoleName.ADMIN && !attempt.getExam().getLecturerId().equals(actor.getId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn chỉ được xem lượt thi của phiên thi do mình tạo");
            }
            List<VivaExamQuestion> questions = examQuestionRepository.findWithQuestionByExamId(attempt.getExam().getId());
            List<InterviewExchangeResponse> result = new ArrayList<>();
            for (InterviewExchange exchange : exchangeRepository.findByAttemptInOrder(attemptId)) {
                result.add(InterviewExchangeResponse.builder()
                        .id(exchange.getId())
                        .parentExchangeId(exchange.getParentExchange() == null ? null : exchange.getParentExchange().getId())
                        .questionNo(questionNoOf(questions, exchange))
                        .depth(exchange.getDepth())
                        .questionText(exchange.getQuestionText())
                        .transcript(exchange.getTranscript())
                        .aiDecision(exchange.getAiDecision())
                        .followUpReason(exchange.getFollowUpReason())
                        .aiDecisionReason(exchange.getAiDecisionReason())
                        .latencyMs(exchange.getLatencyMs())
                        .answerEndedAt(exchange.getAnswerEndedAt() == null ? null : exchange.getAnswerEndedAt().toInstant(ZoneOffset.UTC))
                        .createdAt(AuditTime.toInstant(exchange.getCreatedAt()))
                        .build());
            }
            return result;
        });
    }

    /* ========================= NOI BO ========================= */

    /** Du lieu cua 1 luot thi tai 1 thoi diem. Chi dung trong transaction. */
    private record Snapshot(VivaAttempt attempt, List<VivaExamQuestion> questions, List<InterviewExchange> exchanges) {

        InterviewExchange latest() {
            return exchanges.isEmpty() ? null : exchanges.get(exchanges.size() - 1);
        }
    }

    /** Ket qua buoc 1 cua answer(): da co quyet dinh ngay (khong can AI), hoac du lieu de hoi AI. */
    private record Pending(Decision decision, Context context) {
    }

    private Snapshot load(String attemptId) {
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
        List<VivaExamQuestion> questions = examQuestionRepository.findWithQuestionByExamId(attempt.getExam().getId());
        List<InterviewExchange> exchanges = exchangeRepository.findByAttemptInOrder(attemptId);
        return new Snapshot(attempt, questions, exchanges);
    }

    /** Luot thi phai la cua sinh vien nay va con dang lam (chua ket thuc, chua het gio). */
    private void requireRunning(String attemptId, String studentId) {
        if (examAttemptService.getAttempt(attemptId, studentId).getStatus() == AttemptStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_RUNNING);
        }
    }

    /** Cau dang cho tra loi phai dung la cau FE gui len, tranh tra loi nham cau cu (bam 2 lan, mo 2 tab). */
    private InterviewExchange requireCurrent(Snapshot snapshot, String exchangeId) {
        InterviewExchange latest = snapshot.latest();
        if (latest == null || !latest.getId().equals(exchangeId) || !isWaitingForAnswer(latest)) {
            throw new BusinessException(ErrorCode.INTERVIEW_EXCHANGE_NOT_CURRENT);
        }
        return latest;
    }

    private boolean isWaitingForAnswer(InterviewExchange exchange) {
        return exchange.getTranscript() == null && exchange.getAiDecision() == null;
    }

    private boolean isProcessing(InterviewExchange exchange) {
        return exchange.getTranscript() != null && exchange.getAiDecision() == null;
    }

    private boolean isRunning(VivaAttempt attempt) {
        return attempt.getStatus() == AttemptStatus.IN_PROGRESS && now().isBefore(attempt.getDeadlineAt());
    }

    /**
     * Lan dau co sinh vien bat dau, phien thi chua co cau hoi nao thi gan cau hoi APPROVED vao phien
     * (moi sinh vien cua phien thi cung 1 bo cau hoi):
     *   - Phien co chon chu de: boc ngau nhien question_count cau tu cac chu de do, chia deu giua cac chu de.
     *   - Phien khong chon chu de (phien cu): cau hoi cua nguoi tao phien, bo cau hoi moi nhat truoc;
     *     nguoi tao phien chua co cau nao da duyet thi lay cac cau APPROVED cu nhat trong he thong.
     */
    private void assignDefaultQuestionsIfEmpty(String examId) {
        if (examQuestionRepository.countByExam_Id(examId) > 0) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (examQuestionRepository.countByExam_Id(examId) > 0) {
                    return;
                }
                VivaExam exam = examRepository.findById(examId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_SESSION_NOT_FOUND));
                int count = exam.getQuestionCount() != null ? exam.getQuestionCount() : Math.max(defaultQuestionCount, 1);
                List<Question> picked;
                if (!exam.getTopicIds().isEmpty()) {
                    // Giang vien da chon chu de: boc ngau nhien, chia deu giua cac chu de
                    picked = pickRandomFromTopics(exam.getTopicIds(), count);
                } else {
                    PageRequest limit = PageRequest.of(0, count);
                    picked = questionRepository.findByOwnerAndStatus(exam.getLecturerId(), QUESTION_APPROVED, limit);
                    if (picked.isEmpty()) {
                        picked = questionRepository.findByStatusOrderByCreatedAtAscIdAsc(QUESTION_APPROVED, limit);
                    }
                }
                int orderNo = 1;
                for (Question question : picked) {
                    VivaExamQuestion examQuestion = VivaExamQuestion.builder()
                            .exam(exam)
                            .question(question)
                            .orderNo(orderNo++)
                            .weight(BigDecimal.ONE)
                            .build();
                    examQuestion.setId(idGenerator.next(EXAM_QUESTION_ID_PREFIX));
                    examQuestionRepository.save(examQuestion);
                }
                log.info("Phien thi {} chua co cau hoi, da tu gan {} cau hoi APPROVED", examId, picked.size());
            });
        } catch (DataIntegrityViolationException ex) {
            // 2 sinh vien vao cung luc: request kia da gan cau hoi truoc (dung UNIQUE exam + order_no)
            log.debug("Cau hoi cua phien {} da duoc request khac gan truoc", examId);
        }
    }

    /** Boc ngau nhien toi da count cau APPROVED: lan luot moi chu de 1 cau cho toi khi du hoac het cau. */
    private List<Question> pickRandomFromTopics(Collection<String> topicIds, int count) {
        Map<String, List<Question>> byTopic = new LinkedHashMap<>();
        for (Question question : questionRepository.findByTopic_IdInAndStatus(topicIds, QUESTION_APPROVED)) {
            byTopic.computeIfAbsent(question.getTopic().getId(), key -> new ArrayList<>()).add(question);
        }
        List<List<Question>> groups = new ArrayList<>(byTopic.values());
        Collections.shuffle(groups);
        groups.forEach(Collections::shuffle);

        List<Question> picked = new ArrayList<>();
        boolean added = true;
        for (int round = 0; added && picked.size() < count; round++) {
            added = false;
            for (List<Question> group : groups) {
                if (round < group.size() && picked.size() < count) {
                    picked.add(group.get(round));
                    added = true;
                }
            }
        }
        return picked;
    }

    private InterviewExchange createExchange(VivaAttempt attempt, VivaExamQuestion examQuestion,
                                             InterviewExchange parent, String followUpQuestion) {
        InterviewExchange exchange = InterviewExchange.builder()
                .attempt(attempt)
                .examQuestion(examQuestion)
                .parentExchange(parent)
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .questionText(parent == null ? examQuestion.getQuestion().getContent() : followUpQuestion)
                .build();
        exchange.setId(idGenerator.next(EXCHANGE_ID_PREFIX));
        return exchangeRepository.saveAndFlush(exchange);
    }

    /** Ghi quyet dinh cho luot hoi vua xong, roi tao cau hoi xoay / cau hoi goc tiep theo / ket thuc luot thi. */
    private InterviewStateResponse applyDecision(Snapshot snapshot, InterviewExchange answered, Decision decision,
                                                 Integer latencyMs, String studentId) {
        VivaAttempt attempt = snapshot.attempt();
        answered.setAiDecision(decision.decision());
        answered.setFollowUpReason(decision.isFollowUp() ? decision.reason() : null);
        answered.setAiDecisionReason(decision.explanation());
        answered.setLatencyMs(latencyMs);
        exchangeRepository.saveAndFlush(answered);

        // Het gio trong luc cho AI thi chi ghi quyet dinh, khong hoi them
        if (isRunning(attempt)) {
            if (decision.isFollowUp()) {
                createExchange(attempt, answered.getExamQuestion(), answered, decision.followUpQuestion());
            } else {
                // questionNo dem tu 1 nen cung chinh la vi tri (dem tu 0) cua cau hoi goc ke tiep
                int questionNo = questionNoOf(snapshot.questions(), answered);
                if (questionNo > 0 && questionNo < snapshot.questions().size()) {
                    createExchange(attempt, snapshot.questions().get(questionNo), null, null);
                } else {
                    // Da hoi het cau hoi: ket thuc luot thi
                    examAttemptService.finishAttempt(attempt.getId(), studentId);
                }
            }
        }
        return buildState(load(attempt.getId()), decision.fallback());
    }

    /** Thu tu (bat dau tu 1) cua cau hoi goc ma luot hoi nay thuoc ve. */
    private int questionNoOf(List<VivaExamQuestion> questions, InterviewExchange exchange) {
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getId().equals(exchange.getExamQuestion().getId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private Context buildContext(Snapshot snapshot, InterviewExchange current, String answerText, int remainingFollowUps) {
        Question question = current.getExamQuestion().getQuestion();

        List<Criterion> criteria = new ArrayList<>();
        if (question.getRubric() != null) {
            criterionRepository.findByRubric_IdOrderByOrderNoAsc(question.getRubric().getId())
                    .forEach(item -> criteria.add(new Criterion(item.getName(), item.getDescription())));
        }

        // Cac luot hoi - dap cua CUNG cau hoi goc nay, theo thu tu: cau goc, hoi xoay 1, hoi xoay 2...
        List<Turn> turns = new ArrayList<>();
        for (InterviewExchange exchange : snapshot.exchanges()) {
            if (!exchange.getExamQuestion().getId().equals(current.getExamQuestion().getId())) {
                continue;
            }
            boolean isCurrent = exchange.getId().equals(current.getId());
            turns.add(new Turn(exchange.getQuestionText(), isCurrent ? answerText : exchange.getTranscript()));
        }
        return new Context(question.getModelAnswer(), question.getExpectedKeywords(), criteria, turns, remainingFollowUps);
    }

    private InterviewStateResponse buildState(Snapshot snapshot, boolean aiFallback) {
        InterviewExchange latest = snapshot.latest();
        if (!isRunning(snapshot.attempt()) || latest == null) {
            return completedState(aiFallback).toBuilder().totalQuestions(snapshot.questions().size()).build();
        }
        if (isProcessing(latest)) {
            return InterviewStateResponse.builder()
                    .status(InterviewStatus.PROCESSING)
                    .aiEnabled(llmClient.isEnabled())
                    .totalQuestions(snapshot.questions().size())
                    .build();
        }
        if (!isWaitingForAnswer(latest)) {
            return completedState(aiFallback).toBuilder().totalQuestions(snapshot.questions().size()).build();
        }

        VivaExam exam = snapshot.attempt().getExam();
        Question question = latest.getExamQuestion().getQuestion();
        return InterviewStateResponse.builder()
                .status(InterviewStatus.QUESTION)
                .aiEnabled(llmClient.isEnabled())
                .aiFallback(aiFallback)
                .totalQuestions(snapshot.questions().size())
                .exchangeId(latest.getId())
                .type(latest.getDepth() == 0 ? "MAIN" : "FOLLOW_UP")
                .questionText(latest.getQuestionText())
                .questionNo(questionNoOf(snapshot.questions(), latest))
                .followUpNo(latest.getDepth())
                .maxFollowUps(exam.getMaxFollowUpPerQuestion())
                .bloomLevel(question.getBloomLevel())
                .rubricName(question.getRubric() == null ? null : question.getRubric().getName())
                .prepareSeconds(exam.getPrepareSeconds())
                .answerSeconds(exam.getAnswerSeconds())
                .build();
    }

    private InterviewStateResponse completedState(boolean aiFallback) {
        return InterviewStateResponse.builder()
                .status(InterviewStatus.COMPLETED)
                .aiEnabled(llmClient.isEnabled())
                .aiFallback(aiFallback)
                .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
