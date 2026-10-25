package com.aives.modules.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class RejectQuestionRequest {

    @Schema(example = "Nội dung câu hỏi quá chung chung, không bám sát giáo trình.", description = "Lý do từ chối câu hỏi")
    private String reason;
}
