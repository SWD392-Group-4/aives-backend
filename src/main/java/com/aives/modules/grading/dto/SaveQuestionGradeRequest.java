package com.aives.modules.grading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
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
public class SaveQuestionGradeRequest {

    @Schema(description = "Diem giang vien chot cho cau hoi, tu 0 den thang diem cua rubric", example = "8.5")
    @NotNull(message = "Thiếu điểm chốt")
    @DecimalMin(value = "0", message = "Điểm không được âm")
    private BigDecimal finalScore;

    @Schema(description = "Ghi chu giai trinh. Bat buoc khi diem chot lech qua 2 diem so voi diem AI goi y")
    @Size(max = 2000, message = "Ghi chú dài tối đa 2000 ký tự")
    private String lecturerNote;

    @Valid
    @Schema(description = "Diem giang vien chot cho tung tieu chi cua rubric (tuy chon)")
    private List<CriterionScoreInput> criteriaScores;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CriterionScoreInput {
        @Schema(description = "ID ban ghi criteria_grades")
        private String criteriaGradeId;

        @Schema(description = "ID ban ghi rubric_criteria")
        private String rubricCriteriaId;

        @DecimalMin(value = "0", message = "Điểm tiêu chí không được âm")
        private BigDecimal finalScore;
    }
}
