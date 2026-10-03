package com.aives.modules.exam.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.modules.exam.dto.ExamAttemptResponse;
import com.aives.modules.exam.dto.JoinExamRequest;
import com.aives.modules.exam.entity.VivaAttempt;
import com.aives.modules.exam.entity.VivaExam;
import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.exam.mapper.ExamMapper;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import com.aives.modules.exam.security.JoinAttemptLimiter;
import com.aives.modules.exam.service.ExamAttemptService;
import com.aives.modules.exam.util.ExamCodeGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExamAttemptServiceImpl implements ExamAttemptService {

    private final VivaExamRepository examRepository;
    private final VivaAttemptRepository attemptRepository;
    private final ExamCodeGenerator codeGenerator;
    private final ExamMapper examMapper;
    private final JoinAttemptLimiter joinAttemptLimiter;
    private final Clock clock;

    /**
     * Khong dat @Transactional o day: neu 2 request cung tao luot thi (bam 2 lan),
     * request sau dung UNIQUE(viva_exam_id, student_id) va duoc doc lai luot thi da co.
     */
    @Override
    public ExamAttemptResponse joinSession(String studentId, JoinExamRequest request) {
        // 1. Dang bi khoa vi nhap sai nhieu lan
        long lockedSeconds = joinAttemptLimiter.secondsUntilUnlocked(studentId);
        if (lockedSeconds > 0) {
            throw lockedException(lockedSeconds);
        }

        // 2. Sai dinh dang: bao ngay, khong tinh la 1 lan nhap sai
        String examCode = request.getExamCode().trim().toUpperCase(Locale.ROOT);
        String passcode = request.getPasscode().trim();
        if (!ExamCodeGenerator.EXAM_CODE_PATTERN.matcher(examCode).matches()) {
            throw new BusinessException(ErrorCode.EXAM_CODE_INVALID_FORMAT);
        }
        if (!ExamCodeGenerator.PASSCODE_PATTERN.matcher(passcode).matches()) {
            throw new BusinessException(ErrorCode.EXAM_PASSCODE_INVALID_FORMAT);
        }

        // 3. Ma phien khong ton tai va sai ma truy cap tra CUNG 1 loi, de khong do duoc ma phien nao co that
        VivaExam exam = examRepository.findByExamCode(examCode).orElse(null);
        if (exam == null || !constantTimeEquals(exam.getPasscode(), passcode)) {
            int remaining = joinAttemptLimiter.recordFailure(studentId);
            if (remaining == 0) {
                throw lockedException(joinAttemptLimiter.secondsUntilUnlocked(studentId));
            }
            throw new BusinessException(ErrorCode.EXAM_JOIN_INVALID,
                    "Mã phiên thi hoặc mã truy cập không đúng. Bạn còn " + remaining + " lần thử");
        }

        // 4. Tu day sinh vien da co ma dung nen duoc biet trang thai that cua phien
        if (exam.isCancelled()) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_CANCELLED);
        }

        LocalDateTime now = now();

        // 5. Da vao truoc do: tra lai dung luot thi cu, dong ho KHONG chay lai tu dau
        VivaAttempt existing = attemptRepository.findByExam_IdAndStudentId(exam.getId(), studentId).orElse(null);
        if (existing != null) {
            return resume(existing, exam, now);
        }

        // 6. Chua vao lan nao: phien phai dang mo
        if (now.isBefore(exam.getStartAt())) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_NOT_STARTED);
        }
        if (!now.isBefore(exam.getEndAt())) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_ENDED);
        }

        // 7. Han lam bai = luc vao + thoi luong, nhung khong vuot qua gio dong phien
        LocalDateTime deadlineAt = now.plusMinutes(exam.getDurationMinutes());
        if (deadlineAt.isAfter(exam.getEndAt())) {
            deadlineAt = exam.getEndAt();
        }

        VivaAttempt attempt = VivaAttempt.builder()
                .exam(exam)
                .studentId(studentId)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .deadlineAt(deadlineAt)
                .build();
        attempt.setId(codeGenerator.generateAttemptId());

        try {
            return examMapper.toAttemptResponse(attemptRepository.saveAndFlush(attempt), exam, now);
        } catch (DataIntegrityViolationException ex) {
            // Request song song da tao luot thi truoc
            VivaAttempt created = attemptRepository.findByExam_IdAndStudentId(exam.getId(), studentId)
                    .orElseThrow(() -> ex);
            return resume(created, exam, now);
        }
    }

    @Override
    @Transactional
    public ExamAttemptResponse getAttempt(String attemptId, String studentId) {
        // Luot thi cua nguoi khac cung tra "khong tim thay", khong de lo la luot thi do co ton tai
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId)
                .filter(found -> found.getStudentId().equals(studentId))
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));

        if (attempt.getExam().isCancelled()) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_CANCELLED);
        }

        LocalDateTime now = now();
        // Het gio ma DB con IN_PROGRESS thi chot luon trang thai
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && !now.isBefore(attempt.getDeadlineAt())) {
            attempt.setStatus(AttemptStatus.COMPLETED);
            attempt.setCompletedAt(attempt.getDeadlineAt());
            attemptRepository.save(attempt);
        }
        return examMapper.toAttemptResponse(attempt, attempt.getExam(), now);
    }

    private ExamAttemptResponse resume(VivaAttempt attempt, VivaExam exam, LocalDateTime now) {
        if (attempt.getStatus() == AttemptStatus.COMPLETED || !now.isBefore(attempt.getDeadlineAt())) {
            throw new BusinessException(ErrorCode.EXAM_ATTEMPT_FINISHED);
        }
        return examMapper.toAttemptResponse(attempt, exam, now);
    }

    private BusinessException lockedException(long lockedSeconds) {
        long minutes = (lockedSeconds + 59) / 60;
        return new BusinessException(ErrorCode.EXAM_JOIN_LOCKED,
                "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau " + minutes + " phút");
    }

    /** So sanh khong phu thuoc vi tri ky tu sai dau tien, tranh do ma bang cach do thoi gian phan hoi. */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
