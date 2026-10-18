package com.aives.modules.grading.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.grading.dto.LecturerDashboardResponse;
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

@Tag(name = "Lecturer Dashboard", description = "Bảng điều khiển và số liệu tổng quan của giảng viên")
@RestController
@RequestMapping("/api/lecturer/dashboard")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class LecturerDashboardController {

    private final GradingDashboardService gradingDashboardService;

    @Operation(summary = "Lấy dữ liệu thống kê tổng quan ca thi, bài chờ duyệt và phúc khảo của giảng viên")
    @GetMapping
    public ApiResponse<LecturerDashboardResponse> getDashboard(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gradingDashboardService.getLecturerDashboard(principal));
    }
}
