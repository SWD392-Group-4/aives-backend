package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
public class CourseRubricResponse {

    @Schema(example = "RB0123456789", description = "Mã rubric")
    private String id;

    @Schema(example = "SWD392", description = "Mã môn học")
    private String courseId;

    @Schema(example = "Rubric chuẩn môn Kiến trúc phần mềm", description = "Tên bộ rubric")
    private String name;

    @Schema(example = "Tiêu chí chấm thi vấn đáp học kỳ Fall 2026", description = "Mô tả rubric")
    private String description;

    @Schema(example = "10.00", description = "Điểm tối đa")
    private BigDecimal maxScore;

    @Schema(example = "false", description = "Cờ khóa chỉnh sửa khi có ca thi OPEN hoặc CLOSED")
    private Boolean isLocked;

    @Schema(description = "Danh sách tiêu chí con")
    private List<RubricCriteriaDto> criteria;

    @Schema(description = "Rubric đã được giảng viên duyệt chưa (chưa duyệt thì chưa sinh / duyệt câu hỏi được)")
    private Boolean approved;

    @Schema(description = "Thời điểm duyệt rubric")
    private Instant approvedAt;

    private Instant createdAt;
    private Instant updatedAt;
}
