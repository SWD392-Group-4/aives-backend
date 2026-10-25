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
    private String title;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private MaterialRagStatus ragStatus;
    private LocalDateTime createdAt;
}