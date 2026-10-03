package com.aives.modules.exam.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cua so truot: trong 10 phut gan nhat ma sai du 5 lan thi bi khoa
 * cho den khi lan sai cu nhat het han. Nhap dung KHONG xoa bo dem,
 * de khong the "rua" bo dem bang 1 phien khac ma minh biet ma.
 */
@Component
@RequiredArgsConstructor
public class InMemoryJoinAttemptLimiter implements JoinAttemptLimiter {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private final Clock clock;

    // key (id sinh vien) -> thoi diem cac lan nhap sai gan day
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Override
    public long secondsUntilUnlocked(String key) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            Instant now = clock.instant();
            removeExpired(times, now);
            if (times.size() < MAX_FAILURES) {
                return 0;
            }
            long seconds = Duration.between(now, times.peekFirst().plus(WINDOW)).getSeconds();
            return Math.max(seconds, 1);
        }
    }

    @Override
    public int recordFailure(String key) {
        Instant now = clock.instant();
        removeIdleKeys(now);
        Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            removeExpired(times, now);
            times.addLast(now);
            return Math.max(MAX_FAILURES - times.size(), 0);
        }
    }

    private void removeExpired(Deque<Instant> times, Instant now) {
        Instant threshold = now.minus(WINDOW);
        while (!times.isEmpty() && !times.peekFirst().isAfter(threshold)) {
            times.pollFirst();
        }
    }

    // Don cac key khong con lan sai nao trong cua so, tranh map phinh mai
    private void removeIdleKeys(Instant now) {
        Instant threshold = now.minus(WINDOW);
        failures.entrySet().removeIf(entry -> {
            Deque<Instant> times = entry.getValue();
            synchronized (times) {
                return times.isEmpty() || !times.peekLast().isAfter(threshold);
            }
        });
    }
}
