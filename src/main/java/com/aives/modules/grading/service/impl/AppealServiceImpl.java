package com.aives.modules.grading.service.impl;

import com.aives.common.util.AuditTime;
import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.service.UserService;
import com.aives.modules.content.entity.Question;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.grading.dto.AppealResponse;
import com.aives.modules.grading.entity.GradeAppeal;
import com.aives.modules.grading.entity.QuestionGrade;
import com.aives.modules.grading.repository.GradeAppealRepository;
import com.aives.modules.grading.repository.QuestionGradeRepository;
import com.aives.modules.grading.service.AppealService;
import com.aives.modules.grading.service.AttemptGradingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    private static final BigDecimal TEN = BigDecimal.TEN;

    private final GradeAppealRepository appealRepository;
    private final QuestionGradeRepository questionGradeRepository;
    private final VivaAttemptRepository attemptRepository;
    private final AttemptGradingService attemptGradingService;
    private final UserService userService;
    private final RandomIdGenerator idGenerator;
    private final Clock clock;

    @Override
    @Transactional
    public AppealResponse submit(String studentId, String questionGradeId, String reason) {
        // Diem cua nguoi khac cung bao "khong tim thay"
        QuestionGrade grade = questionGradeRepository.findWithAttemptById(questionGradeId)
                .filter(found -> found.getAttempt().getStudentId().equals(studentId))
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_GRADE_NOT_FOUND));
        // Chi phuc khao duoc khi da co diem chinh thuc
        if (!AttemptGradingService.PUBLISHED.equals(grade.getAttempt().getResultStatus()) || grade.getFinalScore() == null) {
            throw new BusinessException(ErrorCode.RESULT_NOT_PUBLISHED);
        }
        if (appealRepository.existsByQuestionGrade_IdAndStatus(questionGradeId, PENDING)) {
            throw new BusinessException(ErrorCode.APPEAL_ALREADY_PENDING);
        }

        GradeAppeal appeal = GradeAppeal.builder()
                .questionGrade(grade)
                .reason(reason.trim())
                .status(PENDING)
                .scoreBefore(grade.getFinalScore())
                .build();
        appeal.setId(idGenerator.next("GA"));
        // Id tu gan nen save() la merge: ban duoc tra ve moi co created_at
        GradeAppeal saved = appealRepository.saveAndFlush(appeal);
        return toResponses(List.of(saved)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppealResponse> listMine(String studentId) {
        return toResponses(appealRepository.findByStudent(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppealResponse> listForReview(UserPrincipal actor) {
        return toResponses(isAdmin(actor)
                ? appealRepository.findAllWithDetails()
                : appealRepository.findByLecturer(actor.getId()));
    }

    @Override
    @Transactional
    public AppealResponse resolve(UserPrincipal actor, String appealId, String status, BigDecimal scoreAfter, String response) {
        GradeAppeal appeal = appealRepository.findWithDetailsById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPEAL_NOT_FOUND));
        QuestionGrade grade = appeal.getQuestionGrade();
        VivaAttempt attempt = grade.getAttempt();
        if (!isAdmin(actor) && !attempt.getExam().getLecturerId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn chỉ được xử lý phúc khảo của phiên thi do mình tạo");
        }
        if (!PENDING.equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.APPEAL_ALREADY_RESOLVED);
        }

        String reply = response.trim();
        if (ACCEPTED.equals(status)) {
            BigDecimal maxScore = maxScoreOf(grade);
            if (scoreAfter == null) {
                throw new BusinessException(ErrorCode.GRADE_SCORE_INVALID, "Chấp thuận phúc khảo thì phải nhập điểm mới");
            }
            BigDecimal newScore = scoreAfter.setScale(2, RoundingMode.HALF_UP);
            if (newScore.signum() < 0 || newScore.compareTo(maxScore) > 0) {
                throw new BusinessException(ErrorCode.GRADE_SCORE_INVALID,
                        "Điểm phải nằm trong khoảng 0 đến " + maxScore.stripTrailingZeros().toPlainString());
            }
            grade.setFinalScore(newScore);
            // DB bat buoc co ghi chu khi diem chot lech qua 2 diem so voi diem AI (BR-GRADE-002):
            // giang vien chua ghi chu thi dung phan hoi phuc khao lam ghi chu
            if (grade.getLecturerNote() == null || grade.getLecturerNote().isBlank()) {
                grade.setLecturerNote("Điều chỉnh sau phúc khảo: " + reply);
            }
            questionGradeRepository.saveAndFlush(grade);
            attemptGradingService.refreshTotals(attempt, questionGradeRepository.findByAttemptInOrder(attempt.getId()));
            attemptRepository.save(attempt);
            appeal.setScoreAfter(newScore);
        } else {
            appeal.setScoreAfter(appeal.getScoreBefore());
        }
        appeal.setStatus(status);
        appeal.setResponse(reply);
        appeal.setResolvedBy(actor.getId());
        appeal.setResolvedAt(LocalDateTime.now(clock));
        appealRepository.saveAndFlush(appeal);
        return toResponses(List.of(appeal)).get(0);
    }

    /** Cac don phai da duoc nap kem diem cau hoi, luot thi, phien thi va cau hoi (hoac dang trong transaction). */
    private List<AppealResponse> toResponses(List<GradeAppeal> appeals) {
        Set<String> userIds = new HashSet<>();
        for (GradeAppeal appeal : appeals) {
            userIds.add(appeal.getQuestionGrade().getAttempt().getStudentId());
            if (appeal.getResolvedBy() != null) {
                userIds.add(appeal.getResolvedBy());
            }
        }
        Map<String, String> names = userService.getFullNamesByIds(userIds);

        List<AppealResponse> result = new ArrayList<>();
        for (GradeAppeal appeal : appeals) {
            QuestionGrade grade = appeal.getQuestionGrade();
            VivaAttempt attempt = grade.getAttempt();
            result.add(AppealResponse.builder()
                    .id(appeal.getId())
                    .questionGradeId(grade.getId())
                    .questionText(grade.getExamQuestion().getQuestion().getContent())
                    .attemptId(attempt.getId())
                    .examId(attempt.getExam().getId())
                    .examTitle(attempt.getExam().getTitle())
                    .studentId(attempt.getStudentId())
                    .studentName(names.get(attempt.getStudentId()))
                    .reason(appeal.getReason())
                    .status(appeal.getStatus())
                    .scoreBefore(appeal.getScoreBefore())
                    .scoreAfter(appeal.getScoreAfter())
                    .maxScore(maxScoreOf(grade))
                    .response(appeal.getResponse())
                    .resolvedBy(appeal.getResolvedBy())
                    .resolvedByName(appeal.getResolvedBy() == null ? null : names.get(appeal.getResolvedBy()))
                    .createdAt(AuditTime.toInstant(appeal.getCreatedAt()))
                    .resolvedAt(appeal.getResolvedAt() == null ? null : appeal.getResolvedAt().toInstant(ZoneOffset.UTC))
                    .build());
        }
        return result;
    }

    private BigDecimal maxScoreOf(QuestionGrade grade) {
        Question question = grade.getExamQuestion().getQuestion();
        return question.getRubric() == null ? TEN : question.getRubric().getMaxScore();
    }

    private boolean isAdmin(UserPrincipal actor) {
        return actor.getRole() == RoleName.ADMIN;
    }
}
