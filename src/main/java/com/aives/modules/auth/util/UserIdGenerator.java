package com.aives.modules.auth.util;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.modules.auth.enums.Role;
import com.aives.modules.auth.repository.UserRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sinh id cho user: prefix theo role + 6 chu so ngau nhien.
 * ADMIN -> ADxxxxxx, LECTURER -> LExxxxxx, STUDENT -> STxxxxxx
 */
@Component
@RequiredArgsConstructor
public class UserIdGenerator {

    private static final int MAX_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;

    public String generate(Role role) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String id = role.getIdPrefix() + String.format("%06d", random.nextInt(1_000_000));
            // Trung id thi random lai
            if (!userRepository.existsById(id)) {
                return id;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không sinh được id cho user, vui lòng thử lại");
    }
}
