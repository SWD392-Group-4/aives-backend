package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
public class RubricCriterionRequest {

    @Schema(example = "Độ chính xác kiến thức", description = "Tên tiêu chí")
    @NotBlank(message = "Tên tiêu chí không được để trống")
    @Size(max = 200, message = "Tên tiêu chí tối đa 200 ký tự")
    private String name;

    @Schema(example = "Nêu đúng định nghĩa, các nguyên lý cơ bản và thuật ngữ chính xác", description = "Mô tả yêu cầu đạt điểm (BR-BANK-003)")
    @NotBlank(message = "Mô tả tiêu chí không được để trống")
    private String description;

    @Schema(example = "50.0", description = "Trọng số phần trăm của tiêu chí (0 < weight <= 100)")
    @NotNull(message = "Trọng số tiêu chí không được để trống")
    @DecimalMin(value = "0.01", message = "Trọng số tiêu chí phải lớn hơn 0")
    @DecimalMax(value = "100.0", message = "Trọng số tiêu chí tối đa 100")
    private BigDecimal weightPercent;
}
