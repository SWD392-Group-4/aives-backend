package com.aives.modules.system.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.modules.system.dto.SystemSettingResponse;
import com.aives.modules.system.dto.UpdateSystemSettingRequest;
import com.aives.modules.system.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Settings", description = "Quản lý cấu hình toàn hệ thống (Admin)")
@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SystemSettingService systemSettingService;

    @Operation(summary = "Lấy toàn bộ danh sách cấu hình hệ thống")
    @GetMapping
    public ApiResponse<List<SystemSettingResponse>> getAllSettings() {
        return ApiResponse.ok(systemSettingService.getAllSettings());
    }

    @Operation(summary = "Cập nhật giá trị cấu hình hệ thống theo key")
    @PutMapping("/{key}")
    public ApiResponse<SystemSettingResponse> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemSettingRequest request) {
        SystemSettingResponse response = systemSettingService.updateSetting(
                key,
                request.getValue(),
                request.getDescription());
        return ApiResponse.ok("Cập nhật cấu hình thành công", response);
    }
}
