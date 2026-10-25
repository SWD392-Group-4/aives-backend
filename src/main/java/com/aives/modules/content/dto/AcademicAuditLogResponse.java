package com.aives.modules.content.dto;

import com.aives.modules.content.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
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
public class AcademicAuditLogResponse {

    @Schema(example = "662a8c1f4e12ab34cd56ef78", description = "ID bản ghi nhật ký")
    private String id;

    @Schema(example = "SWD392", description = "Mã môn học")
    private String courseId;

    @Schema(example = "CHAP_01", description = "Mã chương")
    private String chapterId;

    @Schema(example = "LE0001", description = "Mã người thực hiện")
    private String actorId;

    @Schema(example = "TS. Nguyen Van A", description = "Tên người thực hiện")
    private String actorName;

    @Schema(example = "APPROVE_QUESTION", description = "Hành động thực hiện")
    private AuditAction action;

    @Schema(example = "QUESTION", description = "Thực thể tác động: QUESTION hoặc RUBRIC")
    private String targetEntity;

    @Schema(example = "Q_1001", description = "ID thực thể tác động")
    private String targetId;

    @Schema(description = "Chi tiết dữ liệu tác động")
    private Map<String, Object> details;

    @Schema(description = "Thời điểm ghi nhận nhật ký")
    private Instant createdAt;
}
