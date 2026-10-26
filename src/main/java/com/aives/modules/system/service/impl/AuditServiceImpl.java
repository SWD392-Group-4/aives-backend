package com.aives.modules.system.service.impl;

import com.aives.common.dto.PageResponse;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.system.dto.AuditLogResponse;
import com.aives.modules.system.entity.AuditLog;
import com.aives.modules.system.repository.AuditLogRepository;
import com.aives.modules.system.service.AuditService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final RandomIdGenerator randomIdGenerator;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actorId, String action, String entityName, String entityId,
                    String oldValue, String newValue, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .actorId(actorId != null ? actorId : "SYSTEM")
                    .action(action)
                    .entityType(entityName)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(ipAddress)
                    .build();

            auditLog.setId(randomIdGenerator.next("AL"));
            LocalDateTime now = LocalDateTime.now(clock);
            auditLog.setCreatedAt(now);
            auditLog.setUpdatedAt(now);

            auditLogRepository.save(auditLog);
            log.info("Ghi nhan audit log: id={}, action={}, entity={}/{}",
                    auditLog.getId(), action, entityName, entityId);
        } catch (Exception e) {
            log.error("Khong the ghi audit log cho action={}: {}", action, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(String actorId, String action, String entityName,
                                                       int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(actorId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId.trim()));
        }
        if (StringUtils.hasText(action)) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase(Locale.ROOT)));
        }
        if (StringUtils.hasText(entityName)) {
            String trimmed = entityName.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> {
                var upper = cb.upper(root.get("entityType"));
                if (trimmed.endsWith("S") && !trimmed.equals("STATUS")) {
                    String singular = trimmed.substring(0, trimmed.length() - 1);
                    return cb.or(cb.equal(upper, trimmed), cb.equal(upper, singular));
                }
                return cb.or(cb.equal(upper, trimmed), cb.equal(upper, trimmed + "S"));
            });
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> pageResult = auditLogRepository.findAll(spec, pageable);

        Set<String> actorIds = pageResult.getContent().stream()
                .map(AuditLog::getActorId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Map<String, User> userMap = actorIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (u1, u2) -> u1));

        List<AuditLogResponse> items = pageResult.getContent().stream()
                .map(log -> {
                    User actor = userMap.get(log.getActorId());
                    return AuditLogResponse.builder()
                            .id(log.getId())
                            .actorId(log.getActorId())
                            .actorName(actor != null ? actor.getFullName() : (log.getActorId() != null ? log.getActorId() : "SYSTEM"))
                            .actorEmail(actor != null ? actor.getEmail() : null)
                            .actorRole(actor != null && actor.getRole() != null ? actor.getRole().getName() : null)
                            .action(log.getAction())
                            .entityName(log.getEntityType())
                            .entityType(log.getEntityType())
                            .entityId(log.getEntityId())
                            .oldValue(log.getOldValue())
                            .newValue(log.getNewValue())
                            .ipAddress(log.getIpAddress())
                            .createdAt(log.getCreatedAt())
                            .timestamp(log.getCreatedAt())
                            .build();
                })
                .toList();

        return new PageResponse<>(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages());
    }
}
