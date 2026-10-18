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
public class MaterialResponse {
    private String id;
    private String lecturerId;
    private String lessonId;
    private String lessonTitle;
    private String topicId;
    private String topicName;
    private String title;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String gcsUri;
    private String ragCorpusId;
    private String ragStatus;
    private Integer chunkCount;
    private String errorMessage;
    private Instant createdAt;
}
