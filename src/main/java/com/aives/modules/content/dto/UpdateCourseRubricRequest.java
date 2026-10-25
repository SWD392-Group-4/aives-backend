package com.aives.modules.content.dto;

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

    @NotBlank(message = "Tên Rubric không được để trống")
    @Size(max = 200, message = "Tên Rubric tối đa 200 ký tự")
    private String name;

    private String description;

    @NotEmpty(message = "Danh sách tiêu chí con không được rỗng")
    @Valid
    private List<RubricCriteriaDto> criteria;
}