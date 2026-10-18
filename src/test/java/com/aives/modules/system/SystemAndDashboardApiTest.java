package com.aives.modules.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.modules.system.service.AuditService;
import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AiTestBeans.class)
@ActiveProfiles("test")
public class SystemAndDashboardApiTest extends ApiTestSupport {

    @Autowired
    private AuditService auditService;

    @Test
    void testSystemSettingsFlow() throws Exception {
        // Admin gets settings
        mockMvc.perform(auth(get("/api/admin/settings"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Admin updates a setting
        Map<String, Object> body = Map.of(
                "value", "gemini-3.8-flash",
                "description", "Model chính cho phỏng vấn"
        );

        mockMvc.perform(json(auth(put("/api/admin/settings/AI_MODEL"), ADMIN), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("AI_MODEL"))
                .andExpect(jsonPath("$.data.value").value("gemini-3.8-flash"));

        // Student is forbidden
        mockMvc.perform(auth(get("/api/admin/settings"), student(1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAuditLogs() throws Exception {
        // Ghi log thu
        auditService.log("AD0000000001", "OVERRIDE_SCORE", "question_grades", "QG001", "5.0", "7.0", "127.0.0.1");

        // Admin doc audit log
        mockMvc.perform(auth(get("/api/admin/audit-logs?action=OVERRIDE_SCORE"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        // Lecturer is forbidden
        mockMvc.perform(auth(get("/api/admin/audit-logs"), LECTURER_1))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminDashboard() throws Exception {
        mockMvc.perform(auth(get("/api/admin/dashboard"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.aiStatus.provider").value("Google Gemini"));
    }

    @Test
    void testLecturerDashboard() throws Exception {
        mockMvc.perform(auth(get("/api/lecturer/dashboard"), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openSessions").isNumber())
                .andExpect(jsonPath("$.data.pendingReviewAttempts").isNumber())
                .andExpect(jsonPath("$.data.pendingAppeals").isNumber());
    }

    @Test
    void testStudentDashboard() throws Exception {
        mockMvc.perform(auth(get("/api/student/dashboard"), student(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.upcomingOrOngoingSessions").isNumber())
                .andExpect(jsonPath("$.data.completedAttempts").isNumber());
    }
}
