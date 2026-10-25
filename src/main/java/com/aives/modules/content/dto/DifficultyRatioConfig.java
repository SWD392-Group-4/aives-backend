package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(example = "40", description = "Tỷ lệ phần trăm câu hỏi Dễ (0 - 100)")
    @NotNull(message = "Tỷ lệ câu hỏi Dễ không được để trống")
    @Min(value = 0, message = "Tỷ lệ câu hỏi Dễ tối thiểu là 0%")
    @Max(value = 100, message = "Tỷ lệ câu hỏi Dễ tối đa là 100%")
    private Integer easyPercent;

    @Schema(example = "40", description = "Tỷ lệ phần trăm câu hỏi Trung bình (0 - 100)")
    @NotNull(message = "Tỷ lệ câu hỏi Trung bình không được để trống")
    @Min(value = 0, message = "Tỷ lệ câu hỏi Trung bình tối thiểu là 0%")
    @Max(value = 100, message = "Tỷ lệ câu hỏi Trung bình tối đa là 100%")
    private Integer mediumPercent;

    @Schema(example = "20", description = "Tỷ lệ phần trăm câu hỏi Khó (0 - 100)")
    @NotNull(message = "Tỷ lệ câu hỏi Khó không được để trống")
    @Min(value = 0, message = "Tỷ lệ câu hỏi Khó tối thiểu là 0%")
    @Max(value = 100, message = "Tỷ lệ câu hỏi Khó tối đa là 100%")
    private Integer hardPercent;
}
