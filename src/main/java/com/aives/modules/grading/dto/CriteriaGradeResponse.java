package com.aives.modules.grading.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Diem 1 tieu chi cua rubric cho 1 cau hoi. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriteriaGradeResponse {
    private String id;
    private String questionGradeId;
    private String rubricCriteriaId;
    private String criterionName;
    private BigDecimal weightPercent;
    private BigDecimal aiScore;
    private BigDecimal finalScore;
    // Doan trich nguyen van tu cau tra loi cua sinh vien (BR-GRADE-001)
    private String evidenceQuote;
    private String rationale;
}
