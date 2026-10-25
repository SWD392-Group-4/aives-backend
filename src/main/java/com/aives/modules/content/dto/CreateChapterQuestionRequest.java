package com.aives.modules.content.dto;

import com.aives.modules.content.enums.QuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class CreateChapterQuestionRequest {

    @Schema(example = "Trình bày nguyên lý Dependency Inversion trong SOLID và cách áp dụng.", description = "Nội dung câu hỏi")
    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @Schema(example = "MEDIUM", description = "Độ khó: EASY, MEDIUM, HARD")
    @NotNull(message = "Độ khó không được để trống")
    private QuestionDifficulty difficulty;

    @Schema(example = "High-level modules should not depend on low-level modules...", description = "Đáp án mẫu")
    private String modelAnswer;

    @Schema(example = "[\"dependency inversion\", \"interface\", \"decoupling\"]", description = "Danh sách từ khóa kỳ vọng")
    private List<String> expectedKeywords;
}
