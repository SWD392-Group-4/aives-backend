package com.aives.modules.vivaroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test luong phong van AI tren H2 (profile dev). KHONG goi Gemini that:
 * LlmClient duoc thay bang ScriptedLlmClient, moi test tu xep san cac cau tra loi cua "AI".
 * Cau hoi lay tu du lieu mau cua SampleContentInitializer (3 cau APPROVED dau tien), phien thi mac dinh toi da 2 cau hoi xoay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AiTestBeans.class)
class VivaInterviewApiTest extends ApiTestSupport {

    // Moi test cach test truoc 2 ngay de phien thi cua test cu khong anh huong
    private static Instant nextBase = Instant.parse("2027-03-01T01:00:00Z");

    @BeforeEach
    void reset() {
        clock.set(nextBase);
        nextBase = nextBase.plus(2, ChronoUnit.DAYS);
        llm.reset();
    }

    /* ========================= BAT DAU ========================= */

    @Test
    void start_returnsFirstQuestionAndIsIdempotent() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);

        JsonNode first = data(startInterview(student, attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUESTION"))
                .andExpect(jsonPath("$.data.type").value("MAIN"))
                .andExpect(jsonPath("$.data.exchangeId", matchesPattern("^IE\\d{10}$")))
                .andExpect(jsonPath("$.data.questionText").isNotEmpty())
                .andExpect(jsonPath("$.data.questionNo").value(1))
                .andExpect(jsonPath("$.data.totalQuestions").value(3))
                .andExpect(jsonPath("$.data.followUpNo").value(0))
                .andExpect(jsonPath("$.data.maxFollowUps").value(2))
                .andExpect(jsonPath("$.data.prepareSeconds").value(30))
                .andExpect(jsonPath("$.data.answerSeconds").value(120))
                .andExpect(jsonPath("$.data.aiEnabled").value(true))
                // Khong lo dap an cho sinh vien
                .andExpect(jsonPath("$.data.modelAnswer").doesNotExist())
                .andExpect(jsonPath("$.data.expectedKeywords").doesNotExist()));

        // Tai lai trang: van la cau hoi do, khong tao cau moi
        JsonNode second = data(startInterview(student, attemptId).andExpect(status().isOk()));
        assertThat(second.get("exchangeId").asText()).isEqualTo(first.get("exchangeId").asText());
        assertThat(llm.calls()).isZero();
    }

    /* ========================= HOI XOAY ========================= */

    @Test
    void answer_followsUpUntilLimitThenMovesToNextQuestion() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));

        // Lan 1: AI hoi xoay
        llm.willReturn(followUpJson("INCOMPLETE", "Bạn hãy nêu cụ thể một nhược điểm của cách làm đó?"));
        JsonNode followUp1 = data(answer(student, attemptId, main.get("exchangeId").asText(), "Câu trả lời đầu tiên")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUESTION"))
                .andExpect(jsonPath("$.data.type").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.questionText").value("Bạn hãy nêu cụ thể một nhược điểm của cách làm đó?"))
                .andExpect(jsonPath("$.data.questionNo").value(1))
                .andExpect(jsonPath("$.data.followUpNo").value(1))
                .andExpect(jsonPath("$.data.aiFallback").value(false)));
        assertThat(followUp1.get("exchangeId").asText()).isNotEqualTo(main.get("exchangeId").asText());

        // Lan 2: AI hoi xoay tiep, va duoc gui kem ca phan hoi - dap truoc do
        llm.willReturn(followUpJson("AMBIGUOUS", "Ý bạn là gì khi nói như vậy?"));
        JsonNode followUp2 = data(answer(student, attemptId, followUp1.get("exchangeId").asText(), "Câu trả lời thứ hai")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.followUpNo").value(2)));
        assertThat(llm.lastPrompt())
                .contains(main.get("questionText").asText())
                .contains("Câu trả lời đầu tiên")
                .contains("Câu hỏi xoáy 1: Bạn hãy nêu cụ thể một nhược điểm của cách làm đó?")
                .contains("Câu trả lời thứ hai");
        assertThat(llm.calls()).isEqualTo(2);

        // Lan 3: da du 2 cau hoi xoay -> KHONG hoi AI nua, sang cau hoi goc thu 2
        answer(student, attemptId, followUp2.get("exchangeId").asText(), "Câu trả lời thứ ba")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUESTION"))
                .andExpect(jsonPath("$.data.type").value("MAIN"))
                .andExpect(jsonPath("$.data.questionNo").value(2))
                .andExpect(jsonPath("$.data.followUpNo").value(0));
        assertThat(llm.calls()).isEqualTo(2);
    }

    @Test
    void answer_aiErrorFallsBackToNextQuestion() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));

        llm.willFail("Gemini trả lỗi HTTP 429");
        answer(student, attemptId, main.get("exchangeId").asText(), "Câu trả lời")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUESTION"))
                .andExpect(jsonPath("$.data.type").value("MAIN"))
                .andExpect(jsonPath("$.data.questionNo").value(2))
                .andExpect(jsonPath("$.data.aiFallback").value(true));
        // Loi goi AI thi khong goi lai
        assertThat(llm.calls()).isEqualTo(1);
    }

    @Test
    void answer_invalidAiResultIsRetriedOnceThenFallsBack() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));
        String tooLong = "từ ".repeat(41).trim() + "?";

        // Cau hoi xoay dai qua 40 tu: goi lai 1 lan, lan 2 hop le thi dung
        llm.willReturn(followUpJson("INCOMPLETE", tooLong));
        llm.willReturn(followUpJson("INCOMPLETE", "Bạn giải thích rõ hơn được không?"));
        JsonNode followUp = data(answer(student, attemptId, main.get("exchangeId").asText(), "Câu trả lời")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.questionText").value("Bạn giải thích rõ hơn được không?")));
        assertThat(llm.calls()).isEqualTo(2);

        // Ca 2 lan deu khong hop le: tu chuyen cau tiep theo
        llm.willReturn(followUpJson("INCOMPLETE", tooLong));
        llm.willReturn("day khong phai JSON");
        answer(student, attemptId, followUp.get("exchangeId").asText(), "Câu trả lời")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MAIN"))
                .andExpect(jsonPath("$.data.questionNo").value(2))
                .andExpect(jsonPath("$.data.aiFallback").value(true));
        assertThat(llm.calls()).isEqualTo(4);
    }

    @Test
    void answer_emptyTranscriptSkipsAi() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));

        answer(student, attemptId, main.get("exchangeId").asText(), "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MAIN"))
                .andExpect(jsonPath("$.data.questionNo").value(2));
        assertThat(llm.calls()).isZero();
    }

    /* ========================= BO QUA / KET THUC ========================= */

    @Test
    void skipAndAnswerAllQuestions_completesAttempt() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode q1 = data(startInterview(student, attemptId));

        JsonNode q2 = data(skip(student, attemptId, q1.get("exchangeId").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MAIN"))
                .andExpect(jsonPath("$.data.questionNo").value(2)));
        assertThat(llm.calls()).isZero();

        // AI chon NEXT (mac dinh cua ScriptedLlmClient)
        JsonNode q3 = data(answer(student, attemptId, q2.get("exchangeId").asText(), "Trả lời câu 2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionNo").value(3)));

        answer(student, attemptId, q3.get("exchangeId").asText(), "Trả lời câu 3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.exchangeId").doesNotExist());

        // Luot thi da duoc chot
        mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // Vao lai chi thay COMPLETED, khong tra loi / bo qua duoc nua
        startInterview(student, attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        expectError(answer(student, attemptId, q3.get("exchangeId").asText(), "Trả lời lại"), 409, "EXAM_ATTEMPT_NOT_RUNNING");
        expectError(skip(student, attemptId, q3.get("exchangeId").asText()), 409, "EXAM_ATTEMPT_NOT_RUNNING");
    }

    @Test
    void answer_afterDeadlineIsRejected() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));

        // Phien thi 60 phut
        clock.advance(Duration.ofMinutes(61));
        expectError(answer(student, attemptId, main.get("exchangeId").asText(), "Trả lời muộn"), 409, "EXAM_ATTEMPT_NOT_RUNNING");
        startInterview(student, attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(llm.calls()).isZero();
    }

    /* ========================= KIEM TRA QUYEN / THU TU ========================= */

    @Test
    void guards_ownerRoleAndCurrentQuestion() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));
        String mainId = main.get("exchangeId").asText();

        // Luot thi cua nguoi khac: bao khong tim thay
        expectError(startInterview(student(2), attemptId), 404, "EXAM_ATTEMPT_NOT_FOUND");
        expectError(answer(student(2), attemptId, mainId, "Trả lời hộ"), 404, "EXAM_ATTEMPT_NOT_FOUND");
        // Giang vien khong duoc thi
        expectError(startInterview(LECTURER_1, attemptId), 403, "ACCESS_DENIED");

        // Thieu / sai ma cau hoi
        expectError(answer(student, attemptId, "", "Trả lời"), 400, "VALIDATION_ERROR");
        expectError(answer(student, attemptId, "IE0000000000", "Trả lời"), 409, "INTERVIEW_EXCHANGE_NOT_CURRENT");
        expectError(skip(student, attemptId, "IE0000000000"), 409, "INTERVIEW_EXCHANGE_NOT_CURRENT");
        expectError(answer(student, attemptId, mainId, "x".repeat(8001)), 400, "VALIDATION_ERROR");

        // Tra loi xong roi gui lai dung cau cu (bam 2 lan): bi tu choi
        answer(student, attemptId, mainId, "Trả lời").andExpect(status().isOk());
        expectError(answer(student, attemptId, mainId, "Trả lời lần nữa"), 409, "INTERVIEW_EXCHANGE_NOT_CURRENT");
        assertThat(llm.calls()).isEqualTo(1);
    }

    /* ========================= GIANG VIEN XEM LAI ========================= */

    @Test
    void lecturerCanReviewExchangesOfOwnSession() throws Exception {
        String student = student(1);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode main = data(startInterview(student, attemptId));

        llm.willReturn(followUpJson("CONTRADICTORY", "Hai ý bạn vừa nêu có mâu thuẫn không?"));
        JsonNode followUp = data(answer(student, attemptId, main.get("exchangeId").asText(), "Trả lời câu gốc"));
        skip(student, attemptId, followUp.get("exchangeId").asText()).andExpect(status().isOk());

        String url = SESSIONS + "/attempts/" + attemptId + "/interview/exchanges";
        mockMvc.perform(auth(get(url), LECTURER_1))
                .andExpect(status().isOk())
                // Cau goc, cau hoi xoay (bi bo qua), cau goc thu 2 dang cho tra loi
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].questionNo").value(1))
                .andExpect(jsonPath("$.data[0].depth").value(0))
                .andExpect(jsonPath("$.data[0].transcript").value("Trả lời câu gốc"))
                .andExpect(jsonPath("$.data[0].aiDecision").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data[0].followUpReason").value("CONTRADICTORY"))
                .andExpect(jsonPath("$.data[0].aiDecisionReason").value("Giải thích của AI"))
                .andExpect(jsonPath("$.data[1].depth").value(1))
                .andExpect(jsonPath("$.data[1].parentExchangeId").value(main.get("exchangeId").asText()))
                .andExpect(jsonPath("$.data[1].questionText").value("Hai ý bạn vừa nêu có mâu thuẫn không?"))
                .andExpect(jsonPath("$.data[1].aiDecision").value("SKIPPED"))
                .andExpect(jsonPath("$.data[2].questionNo").value(2))
                .andExpect(jsonPath("$.data[2].aiDecision").doesNotExist());

        mockMvc.perform(auth(get(url), ADMIN)).andExpect(status().isOk());
        // Giang vien khac va sinh vien khong xem duoc
        expectError(mockMvc.perform(auth(get(url), LECTURER_2)), 403, "ACCESS_DENIED");
        expectError(mockMvc.perform(auth(get(url), student)), 403, "ACCESS_DENIED");
    }
}
