package com.aives.modules.grading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveAppealRequest {

    @Schema(example = "ACCEPTED")
    @NotBlank(message = "Thiếu quyết định xử lý")
    @Pattern(regexp = "ACCEPTED|REJECTED", message = "Quyết định phải là ACCEPTED hoặc REJECTED")
    private String status;

    @Schema(description = "Diem moi cua cau hoi, bat buoc khi ACCEPTED", example = "8.5")
    @DecimalMin(value = "0", message = "Điểm không được âm")
    private BigDecimal scoreAfter;

    @NotBlank(message = "Vui lòng nhập phản hồi cho sinh viên")
    @Size(max = 2000, message = "Phản hồi dài tối đa 2000 ký tự")
    private String response;
}
