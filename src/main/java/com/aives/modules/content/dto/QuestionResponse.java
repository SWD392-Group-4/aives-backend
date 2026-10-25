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
    private String content;
    private QuestionDifficulty difficulty;
    private String modelAnswer;
    private List<String> expectedKeywords;
    private QuestionSource source;
    private QuestionStatus status;
    private String createdBy;
    private String creatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}