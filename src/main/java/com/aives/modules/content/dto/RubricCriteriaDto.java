package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @Schema(example = "RC_01", description = "Mã tiêu chí (nếu cập nhật tiêu chí đã có)")
    private String id;

    @Schema(example = "Khái niệm cốt lõi & Định nghĩa", description = "Tên tiêu chí")
    @NotBlank(message = "Tên tiêu chí không được để trống")
    private String name;

    @Schema(example = "Nêu chính xác định nghĩa, phân biệt được các trường hợp sử dụng.", description = "Tiêu chuẩn chi tiết để đạt điểm")
    @NotBlank(message = "Mô tả tiêu chuẩn tiêu chí không được để trống")
    private String description;

    @Schema(example = "30.0", description = "Trọng số phần trăm (tổng các tiêu chí = 100%)")
    @NotNull(message = "Trọng số phần trăm không được để trống")
    @DecimalMin(value = "0.01", message = "Trọng số phần trăm phải lớn hơn 0")
    @DecimalMax(value = "100.00", message = "Trọng số phần trăm tối đa là 100")
    private BigDecimal weightPercent;

    @Schema(example = "1", description = "Thứ tự hiển thị")
    @NotNull(message = "Thứ tự hiển thị không được để trống")
    private Integer orderNo;
}
