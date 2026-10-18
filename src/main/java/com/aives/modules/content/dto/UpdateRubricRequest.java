package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class UpdateRubricRequest {

    @Schema(example = "Rubric chấm vấn đáp SE cập nhật", description = "Tên rubric")
    @NotBlank(message = "Tên rubric không được để trống")
    @Size(max = 200, message = "Tên rubric tối đa 200 ký tự")
    private String name;

    @Schema(example = "Cập nhật mô tả rubric", description = "Mô tả rubric")
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    @Schema(example = "10.0", description = "Điểm tối đa của rubric")
    @DecimalMin(value = "0.01", message = "Điểm tối đa phải lớn hơn 0")
    private BigDecimal maxScore;

    @Schema(description = "Danh sách tiêu chí đánh giá (tổng weightPercent phải đúng 100%)")
    @NotEmpty(message = "Danh sách tiêu chí không được để trống")
    private List<@Valid RubricCriterionRequest> criteria;
}
