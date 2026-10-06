package com.aives.modules.content.dto;

import java.time.Instant;
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
public class LessonResponse {
    private String id;
    private String lecturerId;
    private String title;
    private String description;
    private int topicCount;
    private int questionCount;
    private Instant createdAt;
}
