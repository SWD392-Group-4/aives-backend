package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @Schema(example = "TP0123456789", description = "Id chủ đề chứa câu hỏi (bỏ trống nếu không đổi)")
    private String topicId;

    @Schema(example = "RB0123456789", description = "Id rubric chấm điểm (truyền chuỗi rỗng để gỡ rubric)")
    private String rubricId;

    @Schema(example = "Trình bày các đặc điểm cốt lõi của phương pháp Agile?", description = "Nội dung câu hỏi")
    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    @Schema(example = "MEDIUM", description = "Độ khó: EASY, MEDIUM, HARD")
    private String difficulty;

    @Schema(example = "UNDERSTAND", description = "Cấp độ tư duy Bloom: REMEMBER, UNDERSTAND, APPLY, ANALYZE")
    private String bloomLevel;

    @Schema(example = "Agile tập trung vào tương tác cá nhân, phần mềm chạy được, cộng tác khách hàng và phản hồi thay đổi.", description = "Câu trả lời mẫu")
    private String modelAnswer;

    @Schema(example = "[\"tương tác cá nhân\", \"phần mềm chạy tốt\", \"thích ứng thay đổi\"]", description = "Từ khóa dự kiến")
    private List<String> expectedKeywords;

    @Schema(example = "APPROVED", description = "Trạng thái câu hỏi: DRAFT, APPROVED, REJECTED, ARCHIVED")
    private String status;
}
