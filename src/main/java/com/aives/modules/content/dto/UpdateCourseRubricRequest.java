package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class UpdateCourseRubricRequest {

    @Schema(example = "Rubric chuẩn môn Kiến trúc phần mềm", description = "Tên bộ rubric")
    @NotBlank(message = "Tên rubric không được để trống")
    @Size(max = 200, message = "Tên rubric tối đa 200 ký tự")
    private String name;

    @Schema(example = "Tiêu chí chấm vấn đáp chuẩn", description = "Mô tả rubric")
    private String description;

    @Schema(description = "Danh sách tiêu chí con; tổng weightPercent bắt buộc phải bằng đúng 100%")
    @NotEmpty(message = "Danh sách tiêu chí không được để trống")
    @Valid
    private List<RubricCriteriaDto> criteria;
}
