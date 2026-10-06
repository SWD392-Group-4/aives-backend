package com.aives.modules.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Luot thi trong man hinh tham dinh (giang vien) va lich su ca thi (sinh vien). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingAttemptResponse {
    private String attemptId;
    private String examId;
    private String title;
    private int durationMinutes;
    private int attemptNo;
    private int maxAttempts;
    private String status;
    // NONE / GRADING / PENDING_REVIEW / GRADING_FAILED / PUBLISHED
    private String resultStatus;
    // Thang 10. Sinh vien chi thay diem khi resultStatus = PUBLISHED.
    private BigDecimal totalAiScore;
    private BigDecimal totalFinalScore;
    private Instant startedAt;
    private Instant deadlineAt;
    private Instant completedAt;
    private Instant publishedAt;
    private Instant serverTime;
    private long remainingSeconds;
    private String studentId;
    private String studentName;
    private String studentEmail;
}
