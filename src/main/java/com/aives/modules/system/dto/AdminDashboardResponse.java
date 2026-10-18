package com.aives.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Số liệu thống kê tổng quan cho quản trị viên")
public class AdminDashboardResponse {

    @Schema(description = "Tổng số tài khoản người dùng trên hệ thống", example = "1420")
    private long totalUsers;

    @Schema(description = "Tổng số quản trị viên", example = "5")
    private long totalAdmins;

    @Schema(description = "Tổng số giảng viên", example = "210")
    private long totalLecturers;

    @Schema(description = "Tổng số sinh viên", example = "1205")
    private long totalStudents;

    @Schema(description = "Tổng số phiên thi đang mở (ONGOING)", example = "8")
    private long ongoingExamSessions;

    @Schema(description = "Trạng thái tích hợp trí tuệ nhân tạo (AI)")
    private AiStatusResponse aiStatus;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Trạng thái chi tiết dịch vụ AI")
    public static class AiStatusResponse {

        @Schema(description = "AI đang được kích hoạt hay tắt", example = "true")
        private boolean enabled;

        @Schema(description = "Nhà cung cấp dịch vụ LLM", example = "Google Gemini")
        private String provider;

        @Schema(description = "Tên mô hình đang sử dụng", example = "gemini-3.8-flash")
        private String model;

        @Schema(description = "Trạng thái hoạt động", example = "ACTIVE")
        private String status;
    }
}
