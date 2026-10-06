package com.aives.common.util;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Sinh id dang chuoi cho cac bang co khoa chinh NVARCHAR(20): tien to 2 chu cai + 10 chu so ngau nhien
 * (vd: IE0482913375). Khong kiem tra trung trong DB vi 10 chu so la du lon;
 * neu trung thi khoa chinh se bao loi va request duoc thu lai.
 */
@Component
public class RandomIdGenerator {

    private static final long BOUND = 10_000_000_000L;

    private final SecureRandom random = new SecureRandom();

    public String next(String prefix) {
        return prefix + String.format("%010d", random.nextLong(BOUND));
    }
}
