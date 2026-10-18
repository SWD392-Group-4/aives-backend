package com.aives.modules.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class GenerateQuestionsFromRagRequest {

    @NotBlank(message = "Chủ đề không được để trống")
    private String topicId;

    @NotEmpty(message = "Cần chọn ít nhất 1 tài liệu để sinh câu hỏi")
    private List<String> materialIds;

    @Builder.Default
    private Integer count = 5;

    @Builder.Default
    private Integer rememberCount = 1;

    @Builder.Default
    private Integer understandCount = 2;

    @Builder.Default
    private Integer applyCount = 1;

    @Builder.Default
    private Integer analyzeCount = 1;
}
