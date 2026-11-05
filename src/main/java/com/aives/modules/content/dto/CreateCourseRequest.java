package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class CreateCourseRequest {

    @Schema(example = "SWD392", description = "Mã môn học (duy nhất)")
    @NotBlank(message = "Mã môn học không được để trống")
    @Size(max = 50, message = "Mã môn học tối đa 50 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Mã môn học chỉ gồm chữ, số và các ký tự _ . -")
    private String courseCode;

    @Schema(example = "Kiến trúc & Thiết kế Phần mềm", description = "Tên môn học")
    @NotBlank(message = "Tên môn học không được để trống")
    @Size(max = 200, message = "Tên môn học tối đa 200 ký tự")
    private String name;

    @Schema(example = "Clean Architecture, Microservices, Design Patterns", description = "Mô tả môn học")
    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;
}
