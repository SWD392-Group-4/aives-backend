package com.aives.modules.content.dto;

import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionSource;
import com.aives.modules.content.enums.QuestionStatus;
import java.time.LocalDateTime;
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
    private String chapterId;
    private String courseId;
    private String topicId;
    private String topicName;
    private String lessonTitle;
    private String rubricId;
    private String rubricName;
    private String content;
    // EASY / MEDIUM / HARD
    private String difficulty;
    // REMEMBER / UNDERSTAND / APPLY / ANALYZE
    private String bloomLevel;
    private String modelAnswer;
    private List<String> expectedKeywords;
    // MANUAL / AI_GENERATED
    private String source;
    // DRAFT / APPROVED / REJECTED / ARCHIVED
    private String status;
    private String createdBy;
    private String creatorName;
    // true = cau hoi co hinh ve (lay hinh qua GET /api/content/questions/{id}/figure)
    private boolean hasFigure;
    private Instant createdAt;
    private Instant updatedAt;
}
