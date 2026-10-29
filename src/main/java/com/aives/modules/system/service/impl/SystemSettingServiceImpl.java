package com.aives.modules.system.service.impl;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.system.dto.SystemSettingResponse;
import com.aives.modules.system.entity.SystemSetting;
import com.aives.modules.system.repository.SystemSettingRepository;
import com.aives.modules.system.service.AuditService;
import com.aives.modules.system.service.SystemSettingService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getAllSettings() {
        return systemSettingRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SystemSettingResponse updateSetting(String key, String value, String description) {
        String updaterId = getCurrentUserId();

        SystemSetting setting = systemSettingRepository.findById(key)
                .orElseGet(() -> SystemSetting.builder()
                        .settingKey(key)
                        .build());

        String oldValue = setting.getSettingValue();

        setting.setSettingValue(value);
        if (description != null && !description.isBlank()) {
            setting.setDescription(description);
        }
        setting.setUpdatedBy(updaterId);
        setting.setUpdatedAt(LocalDateTime.now(clock));

        SystemSetting saved = systemSettingRepository.save(setting);
        log.info("Cap nhat cau hinh he thong: key={}, updatedBy={}", key, updaterId);

        auditService.log(
                updaterId != null ? updaterId : "SYSTEM",
                "UPDATE_SETTING",
                "system_settings",
                key,
                oldValue,
                value
        );

        return toResponse(saved);
    }

    private SystemSettingResponse toResponse(SystemSetting setting) {
        return SystemSettingResponse.builder()
                .key(setting.getSettingKey())
                .value(setting.getSettingValue())
                .description(setting.getDescription())
                .updatedBy(setting.getUpdatedBy())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
