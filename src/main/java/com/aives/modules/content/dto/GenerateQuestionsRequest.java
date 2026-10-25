package com.aives.modules.content.dto;

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

    @NotEmpty(message = "Danh sách học liệu RAG không được để trống")
    private List<String> materialIds;

    @NotNull(message = "Số lượng câu hỏi không được để trống")
    @Min(value = 1, message = "Số lượng câu hỏi tối thiểu là 1")
    @Max(value = 50, message = "Số lượng câu hỏi tối đa là 50")
    private Integer totalQuestions;

    @NotNull(message = "Cấu hình tỷ lệ độ khó không được để trống")
    @Valid
    private DifficultyRatioConfig difficultyRatio;
}