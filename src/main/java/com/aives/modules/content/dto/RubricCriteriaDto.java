package com.aives.modules.content.dto;

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
public class RubricCriteriaDto {

    private String id;

    @NotBlank(message = "Tên tiêu chí không được để trống")
    @Size(max = 200, message = "Tên tiêu chí tối đa 200 ký tự")
    private String name;

    @NotBlank(message = "Mô tả tiêu chí không được để trống (BR-BANK-003)")
    private String description;

    @NotNull(message = "Trọng số phần trăm không được để trống")
    @DecimalMin(value = "0.01", message = "Trọng số tối thiểu là 0.01%")
    @DecimalMax(value = "100.00", message = "Trọng số tối đa là 100.00%")
    private BigDecimal weightPercent;

    @NotNull(message = "Thứ tự tiêu chí không được để trống")
    private Integer orderNo;
}