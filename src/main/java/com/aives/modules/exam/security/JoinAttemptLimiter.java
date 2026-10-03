package com.aives.modules.exam.security;

/**
 * Chan do ma truy cap (brute-force) khi sinh vien vao phien thi.
 * Hien tai luu trong RAM, sau nay co the doi sang Redis.
 */
public interface JoinAttemptLimiter {

    /** So giay con bi khoa; 0 neu khong bi khoa. */
    long secondsUntilUnlocked(String key);

    /** Ghi nhan 1 lan nhap sai. Tra ve so lan con duoc thu truoc khi bi khoa (0 = vua bi khoa). */
    int recordFailure(String key);
}
