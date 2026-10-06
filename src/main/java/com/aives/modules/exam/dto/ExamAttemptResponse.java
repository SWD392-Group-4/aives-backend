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
    // Id phien thi (AIVES_EXAM_yyyy_xxxxxx)
    private String examId;
    private String title;
    private int durationMinutes;
    // Day la luot thi thu may cua sinh vien, tren toi da bao nhieu luot
    private int attemptNo;
    private int maxAttempts;
    private AttemptStatus status;
    private Instant startedAt;
    private Instant deadlineAt;
    // Khac null khi luot thi da ket thuc (sinh vien bam ket thuc hoac het gio)
    private Instant completedAt;
    private Instant serverTime;
    private long remainingSeconds;
}
