package com.aives.modules.system.service;

import com.aives.modules.system.dto.SystemSettingResponse;
import java.util.List;

public interface SystemSettingService {

    List<SystemSettingResponse> getAllSettings();

    SystemSettingResponse updateSetting(String key, String value, String description);
}
