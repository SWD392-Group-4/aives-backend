package com.aives.modules.exam.util;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.modules.exam.repository.VivaAttemptRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sinh cac ma ngau nhien cua module exam:
 *   - id phien thi:   AIVES_EXAM_yyyy_xxxxxx (vd: AIVES_EXAM_2026_049282), yyyy la nam tao.
 *                     Day cung la ma vao thi sinh vien nhap.
 *   - id luot thi:    AT + 8 chu so        (vd: AT04829137)
 *   - ma truy cap:    6 chu so             (vd: 073915)
 */
@Component
@RequiredArgsConstructor
public class ExamCodeGenerator {

    public static final Pattern EXAM_ID_PATTERN = Pattern.compile("^AIVES_EXAM_\\d{4}_\\d{6}$");
    public static final Pattern PASSCODE_PATTERN = Pattern.compile("^\\d{6}$");

    private static final String EXAM_ID_PREFIX = "AIVES_EXAM_";
    private static final String ATTEMPT_ID_PREFIX = "AT";
    // Nam trong id phien thi tinh theo gio Viet Nam
    private static final ZoneId CODE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final VivaExamRepository examRepository;
    private final VivaAttemptRepository attemptRepository;

    /** Id phien thi, dong thoi la ma vao thi: AIVES_EXAM_yyyy_xxxxxx (yyyy la nam tao theo gio Viet Nam). */
    public String generateExamId() {
        int year = clock.instant().atZone(CODE_ZONE).getYear();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String id = EXAM_ID_PREFIX + year + "_" + String.format("%06d", random.nextInt(1_000_000));
            // Trung id thi random lai
            if (!examRepository.existsById(id)) {
                return id;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không sinh được mã phiên thi, vui lòng thử lại");
    }

    public String generateAttemptId() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String id = ATTEMPT_ID_PREFIX + String.format("%08d", random.nextInt(100_000_000));
            if (!attemptRepository.existsById(id)) {
                return id;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không sinh được id cho lượt thi, vui lòng thử lại");
    }

    public String generatePasscode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
