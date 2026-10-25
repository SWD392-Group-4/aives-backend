package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class GenerateQuestionsRequest {

    @Schema(description = "Danh sách ID học liệu READY làm ngữ cảnh RAG")
    @NotEmpty(message = "Danh sách ID học liệu không được để trống")
    private List<String> materialIds;

    @Schema(example = "10", description = "Tổng số câu hỏi cần sinh (1 - 50)")
    @NotNull(message = "Tổng số câu hỏi không được để trống")
    @Min(value = 1, message = "Số câu hỏi tối thiểu là 1")
    @Max(value = 50, message = "Số câu hỏi tối đa mỗi lần sinh là 50")
    private Integer totalQuestions;

    @Schema(description = "Cấu hình tỷ lệ phân bổ độ khó (tổng phải bằng 100%)")
    @NotNull(message = "Cấu hình tỷ lệ độ khó không được để trống")
    @Valid
    private DifficultyRatioConfig difficultyRatio;
}
