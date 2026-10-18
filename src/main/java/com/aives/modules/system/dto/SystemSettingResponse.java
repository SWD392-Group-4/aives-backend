package com.aives.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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
@Schema(description = "Thông tin cấu hình hệ thống")
public class SystemSettingResponse {

    @Schema(description = "Mã cấu hình (key)", example = "AI_MODEL")
    private String key;

    @Schema(description = "Giá trị cấu hình (value)", example = "gemini-3.8-flash")
    private String value;

    @Schema(description = "Mô tả mục đích cấu hình", example = "Model Gemini chính dùng cho phỏng vấn và chấm thi")
    private String description;

    @Schema(description = "Mã người dùng cập nhật cuối cùng", example = "AD0000000001")
    private String updatedBy;

    @Schema(description = "Thời gian cập nhật cuối cùng")
    private LocalDateTime updatedAt;
}
