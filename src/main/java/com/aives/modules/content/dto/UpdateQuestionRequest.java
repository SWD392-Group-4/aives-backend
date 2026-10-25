package com.aives.modules.content.dto;

import com.aives.modules.content.enums.QuestionDifficulty;
import jakarta.validation.constraints.NotBlank;
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
public class UpdateQuestionRequest {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @Schema(example = "MEDIUM", description = "Độ khó: EASY, MEDIUM, HARD")
    private String difficulty;

    @Schema(example = "UNDERSTAND", description = "Cấp độ tư duy Bloom: REMEMBER, UNDERSTAND, APPLY, ANALYZE")
    private String bloomLevel;

    private String modelAnswer;

    private List<String> expectedKeywords;
}