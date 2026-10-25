package com.aives.modules.content.service.impl;

import com.aives.modules.content.document.AcademicAuditLog;
import com.aives.modules.content.dto.AcademicAuditLogResponse;
import com.aives.modules.content.repository.AcademicAuditLogRepository;
import com.aives.modules.content.service.IAcademicAuditLogService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicAuditLogServiceImpl implements IAcademicAuditLogService {

    private final AcademicAuditLogRepository auditLogRepository;

    @Async
    @Override
    public void logActivity(AcademicAuditLog logRecord) {
        try {
            if (logRecord.getCreatedAt() == null) {
                logRecord.setCreatedAt(LocalDateTime.now());
            }
            auditLogRepository.save(logRecord);
            log.info("Ghi log kiem toan thanh cong: action={}, actorId={}, targetEntity={}, targetId={}",
                    logRecord.getAction(), logRecord.getActorId(), logRecord.getTargetEntity(), logRecord.getTargetId());
        } catch (Exception ex) {
            log.error("Loi khi ghi log kiem toan vao MongoDB (khong block transaction chinh): {}", ex.getMessage(), ex);
        }
    }

    @Override
    public Page<AcademicAuditLogResponse> getLogsByCourse(String courseId, Pageable pageable) {
        log.info("Lay lich su kiem toan cho mon hoc: courseId={}, page={}, size={}", courseId, pageable.getPageNumber(), pageable.getPageSize());
        Page<AcademicAuditLog> logs = auditLogRepository.findByCourseIdOrderByCreatedAtDesc(courseId, pageable);
        return logs.map(this::mapToResponse);
    }

    private AcademicAuditLogResponse mapToResponse(AcademicAuditLog entity) {
        return AcademicAuditLogResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .chapterId(entity.getChapterId())
                .actorId(entity.getActorId())
                .actorName(entity.getActorName())
                .action(entity.getAction())
                .targetEntity(entity.getTargetEntity())
                .targetId(entity.getTargetId())
                .details(entity.getDetails())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}