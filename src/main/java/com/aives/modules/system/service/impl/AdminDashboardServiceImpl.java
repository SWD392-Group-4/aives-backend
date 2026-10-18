package com.aives.modules.system.service.impl;

import com.aives.integration.ai.GeminiProperties;
import com.aives.integration.ai.LlmClient;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.exam.repository.VivaExamRepository;
import com.aives.modules.system.dto.AdminDashboardResponse;
import com.aives.modules.system.service.AdminDashboardService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final VivaExamRepository vivaExamRepository;
    private final LlmClient llmClient;
    private final GeminiProperties geminiProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalAdmins = userRepository.countByRole_Name(RoleName.ADMIN);
        long totalLecturers = userRepository.countByRole_Name(RoleName.LECTURER);
        long totalStudents = userRepository.countByRole_Name(RoleName.STUDENT);

        LocalDateTime now = LocalDateTime.now(clock);
        long ongoingExamSessions = vivaExamRepository.countOngoingSessions(now);

        boolean aiEnabled = llmClient.isEnabled();
        AdminDashboardResponse.AiStatusResponse aiStatus = AdminDashboardResponse.AiStatusResponse.builder()
                .enabled(aiEnabled)
                .provider("Google Gemini")
                .model(geminiProperties.getModel())
                .status(aiEnabled ? "ACTIVE" : "DISABLED")
                .build();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalAdmins(totalAdmins)
                .totalLecturers(totalLecturers)
                .totalStudents(totalStudents)
                .ongoingExamSessions(ongoingExamSessions)
                .aiStatus(aiStatus)
                .build();
    }
}
