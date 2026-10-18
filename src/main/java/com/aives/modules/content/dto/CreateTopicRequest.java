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
public class CreateTopicRequest {

    @Schema(example = "LS0123456789", description = "Id bài học chứa chủ đề")
    @NotBlank(message = "Id bài học không được để trống")
    private String lessonId;

    @Schema(example = "Mô hình Agile và Scrum", description = "Tên chủ đề")
    @NotBlank(message = "Tên chủ đề không được để trống")
    @Size(max = 200, message = "Tên chủ đề tối đa 200 ký tự")
    private String name;

    @Schema(example = "Các nguyên lý cơ bản của Agile, các vai trò và nghi thức trong Scrum", description = "Mô tả chủ đề")
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
}
