package com.aives.modules.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateChapterRequest {

    @NotBlank(message = "Tên chương không được để trống")
    @Size(max = 200, message = "Tên chương tối đa 200 ký tự")
    private String name;

    @NotNull(message = "Thứ tự chương không được để trống")
    private Integer orderNo;

    private String description;
}