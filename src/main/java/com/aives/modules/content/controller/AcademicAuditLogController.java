package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.dto.PageResponse;
import com.aives.modules.content.dto.AcademicAuditLogResponse;
import com.aives.modules.content.service.AcademicAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit Logs", description = "Truy vấn nhật ký kiểm toán câu hỏi và rubric từ MongoDB (BR-BANK-007)")
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class AcademicAuditLogController {

    private final AcademicAuditLogService academicAuditLogService;

    @Operation(summary = "Lấy nhật ký kiểm toán thao tác câu hỏi và rubric từ MongoDB")
    @GetMapping("/{courseId}/audit-logs")
    public ApiResponse<PageResponse<AcademicAuditLogResponse>> getCourseAcademicAuditLogs(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(academicAuditLogService.getLogsByCourse(courseId, pageable));
    }
}
