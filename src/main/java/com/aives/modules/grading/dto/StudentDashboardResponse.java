package com.aives.modules.grading.dto;

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
@Schema(description = "Số liệu thống kê bảng điều khiển sinh viên")
public class StudentDashboardResponse {

    @Schema(description = "Số ca thi sắp tới hoặc đang mở", example = "2")
    private long upcomingOrOngoingSessions;

    @Schema(description = "Số bài thi đã hoàn thành", example = "4")
    private long completedAttempts;

    @Schema(description = "Điểm trung bình các bài thi đã công bố", example = "8.45")
    private Double averageScore;

    @Schema(description = "Số đơn phúc khảo đang chờ xử lý", example = "1")
    private long pendingAppeals;
}
