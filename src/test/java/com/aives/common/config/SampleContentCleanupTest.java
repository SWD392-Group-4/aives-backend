package com.aives.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.modules.grading.service.AttemptGradingService;
import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test xoa du lieu mau (SEED_SAMPLE_CONTENT=false) tren 1 database H2 RIENG,
 * de khong lam mat cau hoi mau ma cac test khac dang dung.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aives_cleanup;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AiTestBeans.class)
class SampleContentCleanupTest extends ApiTestSupport {

    @Autowired
    private SampleContentInitializer initializer;

    @Autowired
    private AttemptGradingService attemptGradingService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void removeSampleContent_deletesSampleTopicsAndEverythingThatUsedThem() throws Exception {
        clock.set(Instant.parse("2028-06-01T01:00:00Z"));
        llm.reset();
        String student = student(1);

        // 1 luot thi da cham diem tren cau hoi mau, co ca cau hoi xoay (parent_exchange_id)
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode question = data(startInterview(student, attemptId).andExpect(status().isOk()));
        llm.willReturn(followUpJson("INCOMPLETE", "Bạn cho thêm một ví dụ nữa được không?"));
        JsonNode followUp = data(answer(student, attemptId, question.get("exchangeId").asText(), "Số nguyên tố là số có 2 ước")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("FOLLOW_UP")));
        JsonNode state = data(answer(student, attemptId, followUp.get("exchangeId").asText(), "Ví dụ 2, 3, 5"));
        while (!"COMPLETED".equals(state.get("status").asText())) {
            state = data(skip(student, attemptId, state.get("exchangeId").asText()).andExpect(status().isOk()));
        }
        llm.reset();
        llm.willReturn(gradeJson());
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();

        // 1 phien thi khac da chon chu de mau nhung chua ai vao thi
        String topicId = data(mockMvc.perform(auth(get("/api/content/topics"), LECTURER_1))).get(0).get("id").asText();
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Phiên chọn chủ đề mẫu");
        body.put("startAt", start.toString());
        body.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body.put("durationMinutes", 60);
        body.put("topicIds", List.of(topicId));
        body.put("questionCount", 2);
        String sessionWithTopic = data(mockMvc.perform(json(auth(post(SESSIONS), LECTURER_1), body))
                .andExpect(status().isCreated())).get("id").asText();

        assertThat(count("questions")).isEqualTo(5);
        assertThat(count("viva_attempts")).isEqualTo(1);
        assertThat(count("question_grades")).isEqualTo(3);
        assertThat(count("criteria_grades")).isPositive();
        assertThat(count("interview_exchanges")).isGreaterThanOrEqualTo(4);
        assertThat(count("viva_exam_topics")).isEqualTo(1);
        long sessions = count("viva_exams");

        initializer.removeSampleContent();

        for (String table : List.of("lessons", "topics", "questions", "viva_exam_questions", "viva_exam_topics",
                "viva_attempts", "interview_exchanges", "question_grades", "criteria_grades", "grade_appeals")) {
            assertThat(count(table)).as(table).isZero();
        }
        // Phien thi va rubric duoc giu lai
        assertThat(count("viva_exams")).isEqualTo(sessions);
        assertThat(count("rubrics")).isEqualTo(1);
        mockMvc.perform(auth(get("/api/content/topics"), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(auth(get(SESSIONS + "/" + sessionWithTopic), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topics", hasSize(0)));
        mockMvc.perform(auth(get("/api/grading/attempts"), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Chay lai khi khong con du lieu mau: khong loi, khong xoa gi them
        initializer.removeSampleContent();
        assertThat(count("viva_exams")).isEqualTo(sessions);
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }

    /** Diem cho 3 tieu chi cua rubric mau. */
    private String gradeJson() throws Exception {
        List<Map<String, Object>> criteria = new ArrayList<>();
        for (int criterionNo = 1; criterionNo <= 3; criterionNo++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("criterionNo", criterionNo);
            item.put("score", 7);
            item.put("evidenceQuote", "");
            item.put("rationale", "Lý do");
            criteria.add(item);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("criteria", criteria);
        json.put("strengths", "Điểm mạnh");
        json.put("weaknesses", "Điểm yếu");
        json.put("feedback", "Nhận xét");
        return objectMapper.writeValueAsString(json);
    }
}
