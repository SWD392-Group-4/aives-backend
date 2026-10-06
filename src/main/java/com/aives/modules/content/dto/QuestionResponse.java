package com.aives.modules.content.dto;

import java.time.Instant;
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
public class QuestionResponse {
    private String id;
    private String topicId;
    private String topicName;
    private String lessonTitle;
    private String rubricId;
    private String rubricName;
    private String content;
    // REMEMBER / UNDERSTAND / APPLY / ANALYZE
    private String bloomLevel;
    private String modelAnswer;
    private List<String> expectedKeywords;
    // MANUAL / AI_GENERATED
    private String source;
    // DRAFT / APPROVED / REJECTED / ARCHIVED
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
