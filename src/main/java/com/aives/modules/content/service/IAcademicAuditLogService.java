package com.aives.modules.content.service;

import com.aives.modules.content.document.AcademicAuditLog;
import com.aives.modules.content.dto.AcademicAuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IAcademicAuditLogService {

    void logActivity(AcademicAuditLog log);

    Page<AcademicAuditLogResponse> getLogsByCourse(String courseId, Pageable pageable);
}