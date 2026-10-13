package com.aives.modules.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.modules.content.entity.Lesson;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Topic;
import com.aives.modules.content.repository.LessonRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.TopicRepository;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test chon chu de khi tao phien thi (H2, profile dev).
 * LECTURER_1 so huu chu de mau "Số học cơ bản" (5 cau da duyet) cua SampleContentInitializer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AiTestBeans.class)
class ExamTopicApiTest extends ApiTestSupport {

    private static final String SAMPLE_TOPIC = "Số học cơ bản";
    private static final String GEOMETRY_TOPIC_ID = "TPTEST000001";

    private static Instant nextBase = Instant.parse("2028-01-01T01:00:00Z");

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @BeforeEach
    void reset() {
        clock.set(nextBase);
        nextBase = nextBase.plus(2, ChronoUnit.DAYS);
        llm.reset();
    }

    @Test
    void sessionWithTopic_asksRequestedNumberOfQuestionsFromThatTopic() throws Exception {
        JsonNode topic = topicByName(LECTURER_1, SAMPLE_TOPIC);
        assertThat(topic.get("questionCount").asInt()).isEqualTo(5);
        assertThat(topic.get("approvedQuestionCount").asInt()).isEqualTo(5);
        String topicId = topic.get("id").asText();
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);

        JsonNode session = data(create(LECTURER_1, body(start, List.of(topicId), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andExpect(jsonPath("$.data.topics", hasSize(1)))
                .andExpect(jsonPath("$.data.topics[0].id").value(topicId))
                .andExpect(jsonPath("$.data.topics[0].name").value(SAMPLE_TOPIC)));
        String sessionId = session.get("id").asText();

        // Danh sach va chi tiet deu tra chu de cua phien
        mockMvc.perform(auth(get(SESSIONS + "/" + sessionId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topics[0].name").value(SAMPLE_TOPIC));
        JsonNode listed = data(mockMvc.perform(auth(get(SESSIONS).param("keyword", sessionId), LECTURER_1))
                .andExpect(status().isOk())).get("items").get(0);
        assertThat(listed.get("topics").get(0).get("name").asText()).isEqualTo(SAMPLE_TOPIC);
        assertThat(listed.get("questionCount").asInt()).isEqualTo(2);

        clock.set(start);
        String attemptId = join(student(2), session);
        startInterview(student(2), attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestions").value(2));
    }

    @Test
    void create_rejectsUnknownOrForeignTopicsAndTooManyQuestions() throws Exception {
        String topicId = topicByName(LECTURER_1, SAMPLE_TOPIC).get("id").asText();
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);

        // Chu de chi co 5 cau da duyet
        expectError(create(LECTURER_1, body(start, List.of(topicId), 6)), 400, "EXAM_NOT_ENOUGH_QUESTIONS");
        expectError(create(LECTURER_1, body(start, List.of("TP0000000000"), 1)), 400, "EXAM_TOPIC_INVALID");
        // Giang vien khac khong dung duoc chu de cua LECTURER_1, admin thi duoc
        expectError(create(LECTURER_2, body(start, List.of(topicId), 1)), 400, "EXAM_TOPIC_INVALID");
        create(ADMIN, body(start, List.of(topicId), 5)).andExpect(status().isCreated());
        expectError(create(LECTURER_1, body(start, List.of(topicId), 31)), 400, "VALIDATION_ERROR");
        // Gui trung id thi tinh la 1 chu de
        create(LECTURER_1, body(start, List.of(topicId, topicId), 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.topics", hasSize(1)));
    }

    @Test
    void update_changesTopicsOnlyBeforeSessionOpens() throws Exception {
        String topicId = topicByName(LECTURER_1, SAMPLE_TOPIC).get("id").asText();
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        // Tao khong chon chu de (kieu cu) roi chon sau
        String sessionId = data(create(LECTURER_1, body(start, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.topics", hasSize(0)))
                .andExpect(jsonPath("$.data.questionCount").doesNotExist())).get("id").asText();

        update(LECTURER_1, sessionId, body(start, List.of(topicId), 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topics", hasSize(1)))
                .andExpect(jsonPath("$.data.questionCount").value(4));
        // Khong gui topicIds / questionCount: giu nguyen
        update(LECTURER_1, sessionId, body(start, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topics", hasSize(1)))
                .andExpect(jsonPath("$.data.questionCount").value(4));
        expectError(update(LECTURER_1, sessionId, body(start, List.of(topicId), 9)), 400, "EXAM_NOT_ENOUGH_QUESTIONS");

        // Phien da mo: gui lai dung gia tri cu thi duoc, doi thi bi tu choi
        clock.set(start);
        update(LECTURER_1, sessionId, body(start, List.of(topicId), 4)).andExpect(status().isOk());
        expectError(update(LECTURER_1, sessionId, body(start, List.of(topicId), 3)), 409, "EXAM_SESSION_LOCKED_FIELDS");
        expectError(update(LECTURER_1, sessionId, body(start, List.of(), 4)), 409, "EXAM_SESSION_LOCKED_FIELDS");
    }

    @Test
    void sessionWithTwoTopics_splitsQuestionsEvenlyBetweenTopics() throws Exception {
        seedGeometryTopic();
        String sampleTopicId = topicByName(ADMIN, SAMPLE_TOPIC).get("id").asText();
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);

        JsonNode session = data(create(ADMIN, body(start, List.of(sampleTopicId, GEOMETRY_TOPIC_ID), 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.topics", hasSize(2))));
        clock.set(start);
        String attemptId = join(student(2), session);

        List<String> asked = new ArrayList<>();
        JsonNode state = data(startInterview(student(2), attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestions").value(4)));
        for (int index = 0; index < 4; index++) {
            asked.add(state.get("questionText").asText());
            state = data(skip(student(2), attemptId, state.get("exchangeId").asText()).andExpect(status().isOk()));
        }
        assertThat(state.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(asked).doesNotHaveDuplicates();
        // 4 cau / 2 chu de: moi chu de 2 cau; cau con NHAP cua chu de hinh hoc khong bao gio duoc lay
        assertThat(asked.stream().filter(text -> text.startsWith("Hình học")).count()).isEqualTo(2);
        assertThat(asked).doesNotContain("Hình học câu nháp?");
    }

    /* ========================= HELPER ========================= */

    /** 1 chu de hinh hoc cua 1 chu so huu khac (admin thay tat ca): 3 cau da duyet + 1 cau nhap. */
    private void seedGeometryTopic() {
        if (topicRepository.existsById(GEOMETRY_TOPIC_ID)) {
            return;
        }
        Lesson lesson = Lesson.builder().lecturerId("USTEST000001").title("Hình học 6 (test)").build();
        lesson.setId("LSTEST000001");
        lessonRepository.save(lesson);
        Topic topic = Topic.builder().lesson(lesson).name("Hình học phẳng (test)").build();
        topic.setId(GEOMETRY_TOPIC_ID);
        topicRepository.save(topic);
        for (int number = 1; number <= 4; number++) {
            Question question = Question.builder()
                    .topic(topic)
                    .content(number == 4 ? "Hình học câu nháp?" : "Hình học câu " + number + "?")
                    .bloomLevel("REMEMBER")
                    .modelAnswer("Đáp án " + number)
                    .status(number == 4 ? "DRAFT" : "APPROVED")
                    .build();
            question.setId("QSTEST00000" + number);
            questionRepository.save(question);
        }
    }

    private Map<String, Object> body(Instant start, List<String> topicIds, Integer questionCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Phiên thi theo chủ đề");
        body.put("startAt", start.toString());
        body.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body.put("durationMinutes", 60);
        if (topicIds != null) {
            body.put("topicIds", topicIds);
        }
        if (questionCount != null) {
            body.put("questionCount", questionCount);
        }
        return body;
    }

    private ResultActions create(String email, Map<String, Object> body) throws Exception {
        return mockMvc.perform(json(auth(post(SESSIONS), email), body));
    }

    private ResultActions update(String email, String sessionId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(json(auth(put(SESSIONS + "/" + sessionId), email), body));
    }

    private String join(String studentEmail, JsonNode session) throws Exception {
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("examId", session.get("id").asText());
        join.put("passcode", session.get("passcode").asText());
        return data(mockMvc.perform(json(auth(post(SESSIONS + "/join"), studentEmail), join)).andExpect(status().isOk()))
                .get("attemptId").asText();
    }

    private JsonNode topicByName(String email, String name) throws Exception {
        for (JsonNode topic : data(mockMvc.perform(auth(get("/api/content/topics"), email)).andExpect(status().isOk()))) {
            if (name.equals(topic.get("name").asText())) {
                return topic;
            }
        }
        throw new AssertionError("Không thấy chủ đề " + name);
    }
}
