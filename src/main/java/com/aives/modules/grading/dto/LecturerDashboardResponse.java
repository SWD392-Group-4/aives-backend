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
@Schema(description = "Số liệu thống kê bảng điều khiển giảng viên")
public class LecturerDashboardResponse {

    @Schema(description = "Số ca thi đang mở (ONGOING)", example = "3")
    private long openSessions;

    @Schema(description = "Số bài thi đang chờ duyệt điểm (HITL - PENDING_REVIEW)", example = "12")
    private long pendingReviewAttempts;

    @Schema(description = "Số đơn phúc khảo đang chờ xử lý (PENDING)", example = "4")
    private long pendingAppeals;
}
