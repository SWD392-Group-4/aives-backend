package com.aives.modules.exam.dto;

import com.aives.modules.exam.enums.AttemptStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveExamineeResponse {

    private String studentId;
    private String fullName;
    private String email;
    private String attemptId;
    private AttemptStatus status;
    private Integer currentQuestionIndex;
    private Integer totalQuestions;
    private Integer currentExchangeCount;
    private Long remainingSeconds;
    private Boolean isSpeaking;
    private Instant lastActiveAt;
}
