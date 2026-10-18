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
public class UpdateTopicRequest {

    @Schema(example = "Mô hình Agile & Scrum cải tiến", description = "Tên chủ đề")
    @NotBlank(message = "Tên chủ đề không được để trống")
    @Size(max = 200, message = "Tên chủ đề tối đa 200 ký tự")
    private String name;

    @Schema(example = "Cập nhật mô tả chi tiết cho chủ đề", description = "Mô tả chủ đề")
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
}
