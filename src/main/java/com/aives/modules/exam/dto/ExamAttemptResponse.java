package com.aives.modules.exam.dto;

import com.aives.modules.exam.enums.AttemptStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thong tin luot thi tra cho sinh vien. Khong chua passcode.
 * FE dem nguoc theo deadlineAt va serverTime (khong tin dong ho may sinh vien).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttemptResponse {

    private String attemptId;
    private String examCode;
    private String title;
    private int durationMinutes;
    private AttemptStatus status;
    private Instant startedAt;
    private Instant deadlineAt;
    private Instant serverTime;
    private long remainingSeconds;
}
