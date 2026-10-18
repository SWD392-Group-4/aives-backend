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
import org.springframework.util.StringUtils;

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
     * request sau dung UNIQUE(viva_exams_id, student_id, attempt_no) va duoc doc lai luot thi da co.
     */
    @Override
    public ExamAttemptResponse joinSession(String studentId, JoinExamRequest request) {
        return joinSession(studentId, request, request.getPlatform() != null ? request.getPlatform() : "WEB");
    }

    @Override
    public ExamAttemptResponse joinSession(String studentId, JoinExamRequest request, String platform) {
        // 1. Dang bi khoa vi nhap sai nhieu lan
        long lockedSeconds = joinAttemptLimiter.secondsUntilUnlocked(studentId);
        if (lockedSeconds > 0) {
            throw lockedException(lockedSeconds);
        }

        // 2. Sai dinh dang: bao ngay, khong tinh la 1 lan nhap sai
        String examId = request.getExamId().trim().toUpperCase(Locale.ROOT);
        String passcode = request.getPasscode().trim();
        if (!ExamCodeGenerator.EXAM_ID_PATTERN.matcher(examId).matches()) {
            throw new BusinessException(ErrorCode.EXAM_CODE_INVALID_FORMAT);
        }
        if (!ExamCodeGenerator.PASSCODE_PATTERN.matcher(passcode).matches()) {
            throw new BusinessException(ErrorCode.EXAM_PASSCODE_INVALID_FORMAT);
        }

        // 3. Ma phien khong ton tai va sai ma truy cap tra CUNG 1 loi, de khong do duoc ma phien nao co that
        VivaExam exam = examRepository.findById(examId).orElse(null);
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

        // Kiem tra chan thiet bi di dong neu phien thi khong cho phep
        String effectivePlatform = StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : platform;
        if ("MOBILE".equalsIgnoreCase(effectivePlatform) && !Boolean.TRUE.equals(exam.getAllowMobileExam())) {
            throw new BusinessException(ErrorCode.MOBILE_EXAM_NOT_ALLOWED,
                    "Ca thi này không cho phép làm bài trên thiết bị di động. Vui lòng sử dụng máy tính.");
        }

        LocalDateTime now = now();

        // 5. Dang co luot thi chua xong: tra lai dung luot do, dong ho KHONG chay lai tu dau
        VivaAttempt latest = findLatestAttempt(exam.getId(), studentId);
        if (latest != null && isRunning(latest, now)) {
            return examMapper.toAttemptResponse(latest, exam, now);
        }

        // 6. Luot truoc da xong (hoac chua thi lan nao): con luot thi moi duoc vao tiep.
        //    attempt_no danh so lien tuc tu 1 nen attempt_no cua luot moi nhat = so luot da dung.
        int usedAttempts = latest == null ? 0 : latest.getAttemptNo();
        int maxAttempts = exam.getMaxAttempts();
        if (usedAttempts >= maxAttempts) {
            throw new BusinessException(ErrorCode.EXAM_ATTEMPT_LIMIT_REACHED,
                    "Bạn đã dùng hết " + usedAttempts + "/" + maxAttempts + " lượt thi của phiên thi này");
        }

        // 7. Tao luot thi moi thi phien phai dang mo
        if (now.isBefore(exam.getStartAt())) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_NOT_STARTED);
        }
        if (!now.isBefore(exam.getEndAt())) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_ENDED);
        }

        // Luot truoc het gio ma DB con IN_PROGRESS thi chot lai truoc khi mo luot moi
        if (latest != null && latest.getStatus() == AttemptStatus.IN_PROGRESS) {
            latest.setStatus(AttemptStatus.COMPLETED);
            latest.setCompletedAt(latest.getDeadlineAt());
            attemptRepository.save(latest);
        }

        // 8. Han lam bai = luc vao + thoi luong, nhung khong vuot qua gio dong phien
        LocalDateTime deadlineAt = now.plusMinutes(exam.getDurationMinutes());
        if (deadlineAt.isAfter(exam.getEndAt())) {
            deadlineAt = exam.getEndAt();
        }

        VivaAttempt attempt = VivaAttempt.builder()
                .exam(exam)
                .studentId(studentId)
                .attemptNo(usedAttempts + 1)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .deadlineAt(deadlineAt)
                .build();
        attempt.setId(codeGenerator.generateAttemptId());

        try {
            return examMapper.toAttemptResponse(attemptRepository.saveAndFlush(attempt), exam, now);
        } catch (DataIntegrityViolationException ex) {
            // Request song song da tao luot thi nay truoc: tra lai luot do, khong tao trung
            VivaAttempt created = findLatestAttempt(exam.getId(), studentId);
            if (created == null || !isRunning(created, now)) {
                throw ex;
            }
            return examMapper.toAttemptResponse(created, exam, now);
        }
    }

    @Override
    @Transactional
    public ExamAttemptResponse getAttempt(String attemptId, String studentId) {
        VivaAttempt attempt = findOwnAttempt(attemptId, studentId);
        LocalDateTime now = now();
        // Het gio ma DB con IN_PROGRESS thi chot luon trang thai
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && !now.isBefore(attempt.getDeadlineAt())) {
            attempt.setStatus(AttemptStatus.COMPLETED);
            attempt.setCompletedAt(attempt.getDeadlineAt());
            attemptRepository.save(attempt);
        }
        return examMapper.toAttemptResponse(attempt, attempt.getExam(), now);
    }

    @Override
    @Transactional
    public ExamAttemptResponse finishAttempt(String attemptId, String studentId) {
        VivaAttempt attempt = findOwnAttempt(attemptId, studentId);
        LocalDateTime now = now();
        // Da ket thuc tu truoc (hoac da het gio) thi giu nguyen, khong ghi de thoi diem ket thuc
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            attempt.setStatus(AttemptStatus.COMPLETED);
            attempt.setCompletedAt(now.isBefore(attempt.getDeadlineAt()) ? now : attempt.getDeadlineAt());
            attemptRepository.save(attempt);
        }
        return examMapper.toAttemptResponse(attempt, attempt.getExam(), now);
    }

    /** Lay luot thi cua chinh sinh vien dang dang nhap, kem phien thi. */
    private VivaAttempt findOwnAttempt(String attemptId, String studentId) {
        // Luot thi cua nguoi khac cung tra "khong tim thay", khong de lo la luot thi do co ton tai
        VivaAttempt attempt = attemptRepository.findWithExamById(attemptId)
                .filter(found -> found.getStudentId().equals(studentId))
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_ATTEMPT_NOT_FOUND));
        if (attempt.getExam().isCancelled()) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_CANCELLED);
        }
        return attempt;
    }

    private VivaAttempt findLatestAttempt(String examId, String studentId) {
        return attemptRepository.findFirstByExam_IdAndStudentIdOrderByAttemptNoDesc(examId, studentId).orElse(null);
    }

    /** Luot thi con dang lam: chua bam ket thuc va chua het gio. */
    private boolean isRunning(VivaAttempt attempt, LocalDateTime now) {
        return attempt.getStatus() == AttemptStatus.IN_PROGRESS && now.isBefore(attempt.getDeadlineAt());
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
