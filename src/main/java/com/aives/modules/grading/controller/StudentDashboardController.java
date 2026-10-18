package com.aives.modules.grading.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.StudentDashboardResponse;
import com.aives.modules.grading.service.GradingDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Student Dashboard", description = "Bảng điều khiển và số liệu tổng quan của sinh viên")
@RestController
@RequestMapping("/api/student/dashboard")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentDashboardController {

    private final GradingDashboardService gradingDashboardService;

    @Operation(summary = "Lấy dữ liệu thống kê ca thi sắp tới/đang mở, bài thi đã hoàn thành và điểm trung bình")
    @GetMapping
    public ApiResponse<StudentDashboardResponse> getDashboard(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gradingDashboardService.getStudentDashboard(principal));
    }
}
