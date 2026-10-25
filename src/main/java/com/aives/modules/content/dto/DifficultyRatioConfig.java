package com.aives.modules.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class DifficultyRatioConfig {

    @NotNull(message = "Tỷ lệ câu Dễ không được để trống")
    @Min(value = 0, message = "Tỷ lệ câu Dễ tối thiểu 0%")
    @Max(value = 100, message = "Tỷ lệ câu Dễ tối đa 100%")
    private Integer easyPercent;

    @NotNull(message = "Tỷ lệ câu Trung bình không được để trống")
    @Min(value = 0, message = "Tỷ lệ câu Trung bình tối thiểu 0%")
    @Max(value = 100, message = "Tỷ lệ câu Trung bình tối đa 100%")
    private Integer mediumPercent;

    @NotNull(message = "Tỷ lệ câu Khó không được để trống")
    @Min(value = 0, message = "Tỷ lệ câu Khó tối thiểu 0%")
    @Max(value = 100, message = "Tỷ lệ câu Khó tối đa 100%")
    private Integer hardPercent;
}