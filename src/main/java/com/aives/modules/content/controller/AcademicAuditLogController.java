package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.modules.content.dto.AcademicAuditLogResponse;
import com.aives.modules.content.service.IAcademicAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit Logs", description = "Nhật ký kiểm toán hoạt động môn học, ngân hàng đề và Rubric trên MongoDB")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/audit-logs")
@RequiredArgsConstructor
public class AcademicAuditLogController {

    private final IAcademicAuditLogService auditLogService;

    @Operation(summary = "Lấy nhật ký kiểm toán thao tác câu hỏi và rubric từ MongoDB",
            description = "Truy vấn lịch sử hoạt động (BR-BANK-007) đối với ngân hàng câu hỏi dùng chung và Rubric của môn học.")
    @GetMapping
    public ApiResponse<List<AcademicAuditLogResponse>> getCourseAcademicAuditLogs(
            @Parameter(description = "Mã môn học") @PathVariable String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<AcademicAuditLogResponse> responsePage = auditLogService.getLogsByCourse(courseId, pageRequest);
        return ApiResponse.ok("Lấy danh sách log kiểm toán thành công", responsePage.getContent());
    }
}