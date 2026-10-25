package com.aives.modules.content.service;

import com.aives.common.dto.PageResponse;
import com.aives.modules.content.dto.AcademicAuditLogResponse;
import com.aives.modules.content.enums.AuditAction;
import java.util.Map;
import org.springframework.data.domain.Pageable;

public interface AcademicAuditLogService {

    void logActivity(
            String courseId,
            String chapterId,
            String actorId,
            String actorName,
            AuditAction action,
            String targetEntity,
            String targetId,
            Map<String, Object> details);

    PageResponse<AcademicAuditLogResponse> getLogsByCourse(String courseId, Pageable pageable);
}
