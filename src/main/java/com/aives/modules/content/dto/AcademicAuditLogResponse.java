package com.aives.modules.content.dto;

import com.aives.modules.content.enums.AuditAction;
import java.time.LocalDateTime;
import java.util.Map;
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
public class AcademicAuditLogResponse {

    private String id;
    private String courseId;
    private String chapterId;
    private String actorId;
    private String actorName;
    private AuditAction action;
    private String targetEntity;
    private String targetId;
    private Map<String, Object> details;
    private LocalDateTime createdAt;
}