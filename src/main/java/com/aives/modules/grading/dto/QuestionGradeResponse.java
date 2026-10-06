package com.aives.modules.grading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Diem 1 cau hoi cua 1 luot thi, kem diem tung tieu chi va noi dung hoi - dap. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionGradeResponse {
    private String id;
    private String attemptId;
    private String questionId;
    private String questionText;
    private String bloomLevel;
    // Trong so cua cau hoi trong de
    private BigDecimal weight;
    // Thang diem cua rubric (thuong la 10)
    private BigDecimal maxScore;
    private BigDecimal aiScore;
    private BigDecimal finalScore;
    private String aiStrengths;
    private String aiWeaknesses;
    private String aiFeedback;
    private String lecturerNote;
    // AI_DRAFT / APPROVED / FAILED
    private String status;
    private String gradedBy;
    private String gradedByName;
    private Instant gradedAt;
    private List<CriteriaGradeResponse> criteriaGrades;
    private List<GradeExchangeResponse> exchanges;
    // Don phuc khao moi nhat cua cau nay (null neu chua co don nao)
    private AppealSummary appeal;

    /** Tom tat 1 don phuc khao, hien ngay tren cau hoi. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AppealSummary {
        private String id;
        private String status;
        private String reason;
        private String response;
        private BigDecimal scoreBefore;
        private BigDecimal scoreAfter;
        private Instant createdAt;
        private Instant resolvedAt;
    }
}
