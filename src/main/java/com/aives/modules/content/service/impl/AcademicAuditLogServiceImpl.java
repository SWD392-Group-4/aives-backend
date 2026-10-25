package com.aives.modules.content.service.impl;

import com.aives.common.dto.PageResponse;
import com.aives.modules.content.document.AcademicAuditLog;
import com.aives.modules.content.dto.AcademicAuditLogResponse;
import com.aives.modules.content.enums.AuditAction;
import com.aives.modules.content.repository.AcademicAuditLogRepository;
import com.aives.modules.content.service.AcademicAuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicAuditLogServiceImpl implements AcademicAuditLogService {

    private final AcademicAuditLogRepository academicAuditLogRepository;

    @Async
    @Override
    public void logActivity(
            String courseId,
            String chapterId,
            String actorId,
            String actorName,
            AuditAction action,
            String targetEntity,
            String targetId,
            Map<String, Object> details) {
        try {
            AcademicAuditLog auditLog = AcademicAuditLog.builder()
                    .courseId(courseId)
                    .chapterId(chapterId)
                    .actorId(actorId)
                    .actorName(actorName)
                    .action(action != null ? action.name() : null)
                    .targetEntity(targetEntity)
                    .targetId(targetId)
                    .details(details)
                    .createdAt(Instant.now())
                    .build();

            academicAuditLogRepository.save(auditLog);
            log.debug("Persisted AcademicAuditLog to MongoDB: action={}, courseId={}, targetId={}",
                    action, courseId, targetId);
        } catch (Exception ex) {
            log.error("Failed to write AcademicAuditLog to MongoDB for courseId: {}, action: {}", courseId, action, ex);
        }
    }

    @Override
    public PageResponse<AcademicAuditLogResponse> getLogsByCourse(String courseId, Pageable pageable) {
        Page<AcademicAuditLog> page = academicAuditLogRepository.findByCourseIdOrderByCreatedAtDesc(courseId, pageable);
        List<AcademicAuditLogResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private AcademicAuditLogResponse toResponse(AcademicAuditLog doc) {
        AuditAction parsedAction = null;
        if (doc.getAction() != null) {
            try {
                parsedAction = AuditAction.valueOf(doc.getAction());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return AcademicAuditLogResponse.builder()
                .id(doc.getId())
                .courseId(doc.getCourseId())
                .chapterId(doc.getChapterId())
                .actorId(doc.getActorId())
                .actorName(doc.getActorName())
                .action(parsedAction)
                .targetEntity(doc.getTargetEntity())
                .targetId(doc.getTargetId())
                .details(doc.getDetails())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
