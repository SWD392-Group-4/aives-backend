package com.aives.modules.system.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.dto.PageResponse;
import com.aives.modules.system.dto.AuditLogResponse;
import com.aives.modules.system.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Audit Logs", description = "Quản lý và tra cứu nhật ký kiểm toán hệ thống (Admin)")
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditService auditService;

    @Operation(summary = "Tra cứu và phân trang nhật ký kiểm toán")
    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> getAuditLogs(
            @Parameter(description = "Mã người thực hiện")
            @RequestParam(required = false) String actorId,
            @Parameter(description = "Hành động thực hiện (OVERRIDE_SCORE, PUBLISH_RESULT, ...)")
            @RequestParam(required = false) String action,
            @Parameter(description = "Tên thực thể (question_grades, viva_exams, ...)")
            @RequestParam(required = false) String entityName,
            @Parameter(description = "Chỉ số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditService.getAuditLogs(actorId, action, entityName, page, size));
    }
}
