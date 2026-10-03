package com.aives.modules.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dung chung cho tao va sua phien thi.
 * Cac rang buoc lien quan nhieu truong (endAt > startAt, thoi luong <= khoang mo...)
 * duoc kiem tra trong ExamSessionServiceImpl.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExamSessionRequest {

    @Schema(example = "Vấn đáp giữa kỳ SWD392")
    @NotBlank(message = "Tên phiên thi không được để trống")
    @Size(max = 200, message = "Tên phiên thi tối đa 200 ký tự")
    private String title;

    @Schema(example = "Phòng 301, mang theo thẻ sinh viên")
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    @Schema(example = "2026-10-10T08:00:00+07:00", description = "Giờ mở phiên, ISO-8601 có múi giờ")
    @NotNull(message = "Thời gian mở phiên không được để trống")
    private OffsetDateTime startAt;

    @Schema(example = "2026-10-10T17:00:00+07:00", description = "Giờ đóng phiên, ISO-8601 có múi giờ")
    @NotNull(message = "Thời gian đóng phiên không được để trống")
    private OffsetDateTime endAt;

    @Schema(example = "60", description = "Thời lượng làm bài của mỗi sinh viên (phút)")
    @NotNull(message = "Thời lượng làm bài không được để trống")
    @Min(value = 5, message = "Thời lượng làm bài tối thiểu 5 phút")
    @Max(value = 300, message = "Thời lượng làm bài tối đa 300 phút")
    private Integer durationMinutes;
}
