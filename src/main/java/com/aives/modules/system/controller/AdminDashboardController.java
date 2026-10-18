package com.aives.modules.system.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.modules.system.dto.AdminDashboardResponse;
import com.aives.modules.system.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "Bảng điều khiển và số liệu tổng quan hệ thống (Admin)")
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "Lấy dữ liệu thống kê tổng quan hệ thống cho Admin")
    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.ok(adminDashboardService.getDashboardStats());
    }
}
