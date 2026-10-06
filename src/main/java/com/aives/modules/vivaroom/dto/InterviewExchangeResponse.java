package com.aives.modules.vivaroom.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 1 luot hoi - dap, cho giang vien / admin xem lai (co ca quyet dinh va ly do cua AI). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewExchangeResponse {

    private String id;
    private String parentExchangeId;
    private int questionNo;
    // 0 = cau goc, 1.. = cau hoi xoay
    private int depth;
    private String questionText;
    private String transcript;
    // FOLLOW_UP / NEXT / SKIPPED, null khi chua tra loi
    private String aiDecision;
    // INCOMPLETE / AMBIGUOUS / CONTRADICTORY, chi co khi aiDecision = FOLLOW_UP
    private String followUpReason;
    private String aiDecisionReason;
    private Integer latencyMs;
    private Instant answerEndedAt;
    private Instant createdAt;
}
