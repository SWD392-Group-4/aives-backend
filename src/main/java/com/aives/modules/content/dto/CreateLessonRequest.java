package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
public class CreateLessonRequest {

    @Schema(example = "Nhập môn Kỹ thuật Phần mềm", description = "Tiêu đề bài học")
    @NotBlank(message = "Tiêu đề bài học không được để trống")
    @Size(max = 200, message = "Tiêu đề bài học tối đa 200 ký tự")
    private String title;

    @Schema(example = "Tổng quan về quy trình và mô hình phát triển phần mềm", description = "Mô tả bài học")
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
}
