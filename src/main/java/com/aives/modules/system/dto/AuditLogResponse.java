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
@Schema(description = "Thông tin nhật ký kiểm toán hệ thống")
public class AuditLogResponse {

    @Schema(description = "Mã nhật ký kiểm toán", example = "AL0123456789")
    private String id;

    @Schema(description = "Mã người thực hiện", example = "AD0000000001")
    private String actorId;

    @Schema(description = "Họ tên người thực hiện", example = "Nguyễn Văn Admin")
    private String actorName;

    @Schema(description = "Email người thực hiện", example = "admin@aives.edu.vn")
    private String actorEmail;

    @Schema(description = "Hành động thực hiện", example = "OVERRIDE_SCORE")
    private String action;

    @Schema(description = "Tên thực thể / bảng bị tác động", example = "question_grades")
    private String entityName;

    @Schema(description = "Mã định danh thực thể bị tác động", example = "QG0192837465")
    private String entityId;

    @Schema(description = "Dữ liệu trước khi thay đổi (JSON/Text)")
    private String oldValue;

    @Schema(description = "Dữ liệu sau khi thay đổi (JSON/Text)")
    private String newValue;

    @Schema(description = "Địa chỉ IP thực hiện thao tác", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "Thời gian ghi nhận thao tác")
    private LocalDateTime createdAt;
}
