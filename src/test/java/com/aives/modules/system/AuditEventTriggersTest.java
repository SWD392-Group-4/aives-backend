package com.aives.modules.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.common.dto.PageResponse;
import com.aives.modules.grading.service.AttemptGradingService;
import com.aives.modules.system.dto.AuditLogResponse;
import com.aives.modules.system.service.AuditService;
import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AiTestBeans.class)
@ActiveProfiles("dev")
public class AuditEventTriggersTest extends ApiTestSupport {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AttemptGradingService attemptGradingService;

    private Instant nextBase = Instant.parse("2026-11-10T08:00:00Z");

    @BeforeEach
    void resetClock() {
        clock.set(nextBase);
        nextBase = nextBase.plus(2, ChronoUnit.DAYS);
        llm.reset();
    }

    @Test
    void testUserStatusChangeAndDeletionAuditLog() throws Exception {
        // 1. Tao mot student user moi de test
        Map<String, Object> createReq = new LinkedHashMap<>();
        createReq.put("email", "audittest_user@fpt.edu.vn");
        createReq.put("fullName", "Audit Test User");
        createReq.put("role", "STUDENT");

        JsonNode created = data(mockMvc.perform(json(auth(post("/api/admin/users"), ADMIN), createReq))
                .andExpect(status().isCreated()));
        String userId = created.get("user").get("id").asText();

        // Kiem tra da co audit log CREATE_USER
        PageResponse<AuditLogResponse> createLogs = auditService.getAuditLogs(null, "CREATE_USER", "USER", 0, 10);
        assertThat(createLogs.getItems()).anyMatch(log -> log.getEntityId().equals(userId));

        // 2. Vô hiệu hóa người dùng (UPDATE_STATUS -> INACTIVE)
        Map<String, Object> statusReq = Map.of("active", false);
        mockMvc.perform(json(auth(put("/api/admin/users/" + userId + "/status"), ADMIN), statusReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Verify audit log ghi nhan UPDATE_STATUS voi old=ACTIVE va new=INACTIVE
        PageResponse<AuditLogResponse> statusLogs = auditService.getAuditLogs(null, "UPDATE_STATUS", "USER", 0, 10);
        assertThat(statusLogs.getItems()).anyMatch(log ->
                log.getEntityId().equals(userId)
                        && "ACTIVE".equals(log.getOldValue())
                        && "INACTIVE".equals(log.getNewValue()));

        // 3. Xoa nguoi dung (DELETE_USER)
        mockMvc.perform(auth(delete("/api/admin/users/" + userId), ADMIN))
                .andExpect(status().isOk());

        // Verify audit log ghi nhan DELETE_USER
        PageResponse<AuditLogResponse> deleteLogs = auditService.getAuditLogs(null, "DELETE_USER", "USER", 0, 10);
        assertThat(deleteLogs.getItems()).anyMatch(log ->
                log.getEntityId().equals(userId) && log.getOldValue() != null);
    }

    @Test
    void testSystemSettingUpdateAuditLog() throws Exception {
        Map<String, Object> body = Map.of(
                "value", "gemini-1.5-pro",
                "description", "Model chuyen sau cho phong van"
        );

        mockMvc.perform(json(auth(put("/api/admin/settings/TEST_AUDIT_SETTING"), ADMIN), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("TEST_AUDIT_SETTING"))
                .andExpect(jsonPath("$.data.value").value("gemini-1.5-pro"));

        // Verify audit log
        PageResponse<AuditLogResponse> settingLogs = auditService.getAuditLogs(null, "UPDATE_SETTING", "system_settings", 0, 10);
        assertThat(settingLogs.getItems()).anyMatch(log ->
                log.getEntityId().equals("TEST_AUDIT_SETTING") && "gemini-1.5-pro".equals(log.getNewValue()));
    }

    @Test
    void testGradingAndPublishAndAppealAuditLog() throws Exception {
        String student = student(3);
        String attemptId = takeExam(student);

        llm.willReturn(gradeJson(score(1, 8, "", ""), score(2, 8, "", ""), score(3, 8, "", "")));
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();

        List<String> gradeIds = new ArrayList<>();
        data(mockMvc.perform(auth(get("/api/grading/attempts/" + attemptId), LECTURER_1)).andExpect(status().isOk()))
                .get("questionGrades").forEach(grade -> gradeIds.add(grade.get("id").asText()));

        // Chot diem cau 1: OVERRIDE_SCORE
        Map<String, Object> gradeBody = new LinkedHashMap<>();
        gradeBody.put("finalScore", 9.0);
        gradeBody.put("lecturerNote", "Sinh vien tra loi tot");
        mockMvc.perform(json(auth(put("/api/grading/question-grades/" + gradeIds.get(0)), LECTURER_1), gradeBody))
                .andExpect(status().isOk());

        // Verify audit log OVERRIDE_SCORE
        PageResponse<AuditLogResponse> overrideLogs = auditService.getAuditLogs(null, "OVERRIDE_SCORE", "question_grades", 0, 10);
        assertThat(overrideLogs.getItems()).anyMatch(log ->
                log.getEntityId().equals(gradeIds.get(0))
                        && log.getNewValue().contains("9.00"));

        // Chot not cau 2 va 3 (diem 0 vi sinh vien bo qua)
        Map<String, Object> zeroGrade = new LinkedHashMap<>();
        zeroGrade.put("finalScore", 0.0);
        zeroGrade.put("lecturerNote", null);
        mockMvc.perform(json(auth(put("/api/grading/question-grades/" + gradeIds.get(1)), LECTURER_1), zeroGrade))
                .andExpect(status().isOk());
        mockMvc.perform(json(auth(put("/api/grading/question-grades/" + gradeIds.get(2)), LECTURER_1), zeroGrade))
                .andExpect(status().isOk());

        // Cong bo ket qua: PUBLISH_RESULT
        mockMvc.perform(auth(post("/api/grading/attempts/" + attemptId + "/publish"), LECTURER_1))
                .andExpect(status().isOk());

        // Verify audit log PUBLISH_RESULT
        PageResponse<AuditLogResponse> publishLogs = auditService.getAuditLogs(null, "PUBLISH_RESULT", "viva_attempts", 0, 10);
        assertThat(publishLogs.getItems()).anyMatch(log ->
                log.getEntityId().equals(attemptId)
                        && log.getNewValue().contains("PUBLISHED"));

        // Sinh vien nop don phuc khao cau 1
        Map<String, Object> appealBody = new LinkedHashMap<>();
        appealBody.put("questionGradeId", gradeIds.get(0));
        appealBody.put("reason", "Em muon duoc xem xet lai diem cau 1");
        JsonNode appealData = data(mockMvc.perform(json(auth(post("/api/student/appeals"), student), appealBody))
                .andExpect(status().isCreated()));
        String appealId = appealData.get("id").asText();

        // Giang vien chap thuan phuc khao: RESOLVE_APPEAL
        Map<String, Object> resolveBody = new LinkedHashMap<>();
        resolveBody.put("status", "ACCEPTED");
        resolveBody.put("scoreAfter", 9.5);
        resolveBody.put("response", "Dong y nang diem sau khi ra soat transcript");
        mockMvc.perform(json(auth(put("/api/grading/appeals/" + appealId), LECTURER_1), resolveBody))
                .andExpect(status().isOk());

        // Verify audit log RESOLVE_APPEAL
        PageResponse<AuditLogResponse> appealLogs = auditService.getAuditLogs(null, "RESOLVE_APPEAL", "grade_appeals", 0, 10);
        assertThat(appealLogs.getItems()).anyMatch(log ->
                log.getEntityId().equals(appealId)
                        && log.getNewValue().contains("ACCEPTED")
                        && log.getNewValue().contains("9.50"));
    }

    private String takeExam(String student) throws Exception {
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode q1 = data(startInterview(student, attemptId).andExpect(status().isOk()));
        JsonNode q2 = data(answer(student, attemptId, q1.get("exchangeId").asText(),
                "Số nguyên tố là số chỉ chia hết cho 1 và chính nó, ví dụ 2 3 5").andExpect(status().isOk()));
        JsonNode q3 = data(skip(student, attemptId, q2.get("exchangeId").asText()).andExpect(status().isOk()));
        answer(student, attemptId, q3.get("exchangeId").asText(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        llm.reset();
        return attemptId;
    }

    private Map<String, Object> score(int criterionNo, double score, String evidenceQuote, String rationale) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("criterionNo", criterionNo);
        item.put("score", score);
        item.put("evidenceQuote", evidenceQuote);
        item.put("rationale", rationale);
        return item;
    }

    @SafeVarargs
    private String gradeJson(Map<String, Object>... criteria) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("criteria", List.of(criteria));
        json.put("strengths", "Điểm mạnh");
        json.put("weaknesses", "Điểm yếu");
        json.put("feedback", "Nhận xét");
        return objectMapper.writeValueAsString(json);
    }
}
