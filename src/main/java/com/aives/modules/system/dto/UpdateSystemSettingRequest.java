package com.aives.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Yêu cầu cập nhật cấu hình hệ thống")
public class UpdateSystemSettingRequest {

    @Schema(description = "Giá trị mới của cấu hình", example = "gemini-3.8-flash")
    @NotBlank(message = "Giá trị cấu hình không được để trống")
    private String value;

    @Schema(description = "Mô tả mục đích cấu hình", example = "Model Gemini chính")
    private String description;
}
