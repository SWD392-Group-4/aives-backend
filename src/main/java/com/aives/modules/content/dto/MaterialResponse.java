package com.aives.modules.content.dto;

import com.aives.modules.content.enums.MaterialRagStatus;
import java.time.LocalDateTime;
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
    private String chapterId;
    private String lecturerId;
    private String lessonId;
    private String lessonTitle;
    private String topicId;
    private String topicName;
    private String title;
    private String fileUrl;
    private String fileType;
    private String gcsUri;
    private String fileUrl;
    private String ragCorpusId;
    private String ragStatus;
    private Integer chunkCount;
    private String errorMessage;
    private Instant createdAt;
}
