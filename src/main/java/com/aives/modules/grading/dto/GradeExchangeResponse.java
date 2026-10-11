package com.aives.modules.grading.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 1 luot hoi - dap hien kem diem cua cau hoi. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeExchangeResponse {
    private String id;
    private String attemptId;
    private String questionId;
    private String parentExchangeId;
    // 0 = cau goc, 1.. = cau hoi xoay
    private int depth;
    private String questionText;
    private String transcript;
    private String followUpReason;
    private String aiDecision;
    // Chi giang vien thay
    private String aiDecisionReason;
    private Instant answerEndedAt;
    private Integer latencyMs;
    private Instant createdAt;
}
