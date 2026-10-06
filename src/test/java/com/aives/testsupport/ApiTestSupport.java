package com.aives.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ham dung chung cho cac test API co AI: dang nhap bang tai khoan mau cua DataInitializer,
 * tao phien thi, cho sinh vien vao thi va di qua tung buoc phong van.
 */
public abstract class ApiTestSupport {

    protected static final String SESSIONS = "/api/exam-sessions";
    protected static final String LECTURER_1 = "lecturer01@fe.edu.vn";
    protected static final String LECTURER_2 = "lecturer02@fe.edu.vn";
    protected static final String ADMIN = "admin@aives.local";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected ScriptedLlmClient llm;

    private final Map<String, String> tokens = new HashMap<>();

    protected String student(int number) {
        return "student0" + number + "@fpt.edu.vn";
    }

    /** Giang vien tao phien 60 phut, den gio mo phien, sinh vien vao va tra ve attemptId. */
    protected String joinNewSession(String lecturerEmail, String studentEmail) throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Phiên phỏng vấn AI");
        body.put("description", "Mô tả");
        body.put("startAt", start.toString());
        body.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body.put("durationMinutes", 60);
        JsonNode session = data(mockMvc.perform(json(auth(post(SESSIONS), lecturerEmail), body)).andExpect(status().isCreated()));

        clock.set(start);
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("examId", session.get("id").asText());
        join.put("passcode", session.get("passcode").asText());
        return data(mockMvc.perform(json(auth(post(SESSIONS + "/join"), studentEmail), join)).andExpect(status().isOk()))
                .get("attemptId").asText();
    }

    protected ResultActions startInterview(String email, String attemptId) throws Exception {
        return mockMvc.perform(auth(post(interviewUrl(attemptId) + "/start"), email));
    }

    protected ResultActions answer(String email, String attemptId, String exchangeId, String transcript) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exchangeId", exchangeId);
        body.put("transcript", transcript);
        return mockMvc.perform(json(auth(post(interviewUrl(attemptId) + "/answer"), email), body));
    }

    protected ResultActions skip(String email, String attemptId, String exchangeId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exchangeId", exchangeId);
        return mockMvc.perform(json(auth(post(interviewUrl(attemptId) + "/skip"), email), body));
    }

    protected String interviewUrl(String attemptId) {
        return SESSIONS + "/attempts/" + attemptId + "/interview";
    }

    protected String followUpJson(String reason, String question) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("decision", "FOLLOW_UP");
        json.put("reason", reason);
        json.put("followUpQuestion", question);
        json.put("explanation", "Giải thích của AI");
        return objectMapper.writeValueAsString(json);
    }

    protected String passwordOf(String email) {
        return ADMIN.equals(email) ? "Admin@123" : "123456";
    }

    protected String token(String email) throws Exception {
        String cached = tokens.get(email);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("email", email);
        login.put("password", passwordOf(email));
        String token = data(mockMvc.perform(json(post("/api/auth/login"), login)).andExpect(status().isOk()))
                .get("accessToken").asText();
        tokens.put(email, token);
        return token;
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String email) throws Exception {
        return builder.header("Authorization", "Bearer " + token(email));
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected JsonNode data(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content).get("data");
    }

    protected void expectError(ResultActions actions, int httpStatus, String code) throws Exception {
        actions.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code));
    }
}
