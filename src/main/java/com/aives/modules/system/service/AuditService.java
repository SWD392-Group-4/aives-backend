package com.aives.modules.system.service;

import com.aives.common.dto.PageResponse;
import com.aives.modules.system.dto.AuditLogResponse;

public interface AuditService {

    void log(String actorId, String action, String entityName, String entityId, String oldValue, String newValue, String ipAddress);

    default void log(String actorId, String action, String entityName, String entityId, String oldValue, String newValue) {
        log(actorId, action, entityName, entityId, oldValue, newValue, null);
    }

    PageResponse<AuditLogResponse> getAuditLogs(String actorId, String action, String entityName, int page, int size);
}
