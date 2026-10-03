package com.aives.modules.auth.util;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.modules.auth.repository.UserRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sinh ma sinh vien ngau nhien: SE + 6 chu so (vd: SE482913).
 * Dung khi sinh vien tu dang ky hoac admin tao account STUDENT ma khong nhap ma.
 */
@Component
@RequiredArgsConstructor
public class StudentCodeGenerator {

    private static final String PREFIX = "SE";
    private static final int MAX_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String studentCode = PREFIX + String.format("%06d", random.nextInt(1_000_000));
            // Trung ma thi random lai
            if (!userRepository.existsByStudentCode(studentCode)) {
                return studentCode;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không sinh được mã sinh viên, vui lòng thử lại");
    }
}
