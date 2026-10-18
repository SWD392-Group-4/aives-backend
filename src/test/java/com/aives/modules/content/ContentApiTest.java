package com.aives.modules.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.integration.ai.LlmRequest;
import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test ngan hang cau hoi tren H2: gui file rubric va file cau hoi RIENG de "AI" (gia) tach, xem danh sach, duyet cau hoi,
 * va phien thi tu lay bo cau hoi moi nhat da duyet cua nguoi tao phien.
 * Dung LECTURER_2 de nhap cau hoi, khong dung toi du lieu cua LECTURER_1 ma cac test khac dang dung.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AiTestBeans.class)
class ContentApiTest extends ApiTestSupport {

    private static final String CONTENT = "/api/content";
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static Instant nextBase = Instant.parse("2027-09-01T01:00:00Z");

    @BeforeEach
    void reset() {
        clock.set(nextBase);
        nextBase = nextBase.plus(2, ChronoUnit.DAYS);
        llm.reset();
    }

    /* ========================= GUI FILE RUBRIC ========================= */

    @Test
    void importRubrics_createsRubricsOnly() throws Exception {
        llm.willReturn(rubricsJson(
                rubric("Rubric toán", criterion("Đúng kiến thức", 5), criterion("Ví dụ", 3), criterion("Diễn đạt", 2)),
                rubric("", criterion("Một tiêu chí", 7))));
        int lessonsBefore = lessonCount(LECTURER_2);

        JsonNode result = data(uploadRubrics(LECTURER_2, "tieu-chi.docx", DOCX_TYPE,
                docx("Tiêu chí 1: Đúng kiến thức 5 điểm", "Tiêu chí 2: Ví dụ 3 điểm"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("tieu-chi.docx"))
                .andExpect(jsonPath("$.data.rubrics", hasSize(2)))
                .andExpect(jsonPath("$.data.rubrics[0].name").value("Rubric toán"))
                // 5 / 3 / 2 diem duoc doi thanh 50 / 30 / 20 %
                .andExpect(jsonPath("$.data.rubrics[0].criteria[0].weightPercent").value(50.0))
                .andExpect(jsonPath("$.data.rubrics[0].criteria[1].weightPercent").value(30.0))
                .andExpect(jsonPath("$.data.rubrics[0].criteria[2].weightPercent").value(20.0))
                // Rubric khong co ten: dat ten theo thu tu; 1 tieu chi thi la 100%
                .andExpect(jsonPath("$.data.rubrics[1].name").value("Rubric 2"))
                .andExpect(jsonPath("$.data.rubrics[1].criteria[0].weightPercent").value(100.0))
                .andExpect(jsonPath("$.data.questions", hasSize(0)))
                .andExpect(jsonPath("$.data.lesson").doesNotExist()));

        // File rubric khong tao bai hoc / cau hoi nao
        assertThat(lessonCount(LECTURER_2)).isEqualTo(lessonsBefore);
        assertThat(idsOf(LECTURER_2, "/rubrics")).contains(result.get("rubrics").get(0).get("id").asText());

        // Noi dung file Word duoc gui cho AI duoi dang chu, voi cau lenh tach rubric
        LlmRequest request = llm.lastRequest();
        assertThat(request.longRunning()).isTrue();
        assertThat(request.attachments()).isEmpty();
        assertThat(request.systemInstruction()).contains("tiêu chí chấm điểm (rubric)");
        assertThat(request.userPrompt()).contains("tieu-chi.docx").contains("Tiêu chí 1: Đúng kiến thức 5 điểm");

        // File khong co tieu chi nao
        llm.willReturn(rubricsJson());
        expectError(uploadRubrics(LECTURER_2, "rong.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8)),
                400, "IMPORT_NO_RUBRICS");
    }

    /* ========================= GUI FILE CAU HOI ========================= */

    @Test
    void importQuestions_createsDraftQuestionsWithChosenRubric() throws Exception {
        String rubricId = importRubric(LECTURER_2, "Rubric cho bộ đề");
        llm.willReturn(questionsJson("Toán 6", "Số tự nhiên",
                question("Số chẵn là gì?", "REMEMBER", "Số chia hết cho 2.", "chia hết cho 2"),
                // bloomLevel la: he thong tu sua ve UNDERSTAND
                question("Số lẻ là gì?", "KHONG_BIET", "")));

        JsonNode result = data(uploadQuestions(LECTURER_2, "de-toan.docx", DOCX_TYPE,
                docx("Câu 1: Số chẵn là gì?", "Đáp án: Số chia hết cho 2."), rubricId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("de-toan.docx"))
                .andExpect(jsonPath("$.data.lesson.title").value("Toán 6"))
                .andExpect(jsonPath("$.data.topic.name").value("Số tự nhiên"))
                .andExpect(jsonPath("$.data.rubrics", hasSize(1)))
                .andExpect(jsonPath("$.data.rubrics[0].id").value(rubricId))
                .andExpect(jsonPath("$.data.questions", hasSize(2)))
                .andExpect(jsonPath("$.data.questions[0].content").value("Số chẵn là gì?"))
                .andExpect(jsonPath("$.data.questions[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.questions[0].bloomLevel").value("REMEMBER"))
                .andExpect(jsonPath("$.data.questions[0].modelAnswer").value("Số chia hết cho 2."))
                .andExpect(jsonPath("$.data.questions[0].expectedKeywords[0]").value("chia hết cho 2"))
                .andExpect(jsonPath("$.data.questions[0].rubricId").value(rubricId))
                .andExpect(jsonPath("$.data.questions[0].rubricName").value("Rubric cho bộ đề"))
                .andExpect(jsonPath("$.data.questions[1].bloomLevel").value("UNDERSTAND"))
                .andExpect(jsonPath("$.data.questions[1].rubricId").value(rubricId)));

        LlmRequest request = llm.lastRequest();
        assertThat(request.systemInstruction()).contains("danh sách câu hỏi thi");
        assertThat(request.userPrompt()).contains("de-toan.docx").contains("Câu 1: Số chẵn là gì?");

        // Danh sach cau hoi cua chu de vua tao giu dung thu tu trong file
        String topicId = result.get("topic").get("id").asText();
        mockMvc.perform(auth(get(CONTENT + "/questions").param("topicId", topicId), LECTURER_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].content").value("Số chẵn là gì?"))
                .andExpect(jsonPath("$.data[1].content").value("Số lẻ là gì?"));

        // Rubric cua giang vien khac / khong ton tai: bi chan truoc khi goi AI
        int callsBefore = llm.calls();
        expectError(uploadQuestions(LECTURER_1, "de.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8), rubricId),
                404, "RUBRIC_NOT_FOUND");
        expectError(uploadQuestions(LECTURER_2, "de.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8), "RB0000000000"),
                404, "RUBRIC_NOT_FOUND");
        assertThat(llm.calls()).isEqualTo(callsBefore);
    }

    @Test
    void importQuestionsPdf_sendsFileToAi_andRubricIsOptional() throws Exception {
        byte[] pdf = "%PDF-1.7 noi dung gia".getBytes(StandardCharsets.UTF_8);
        // AI khong tim thay ten mon / chu de: lay ten file
        llm.willReturn(questionsJson("", "", question("Câu hỏi trong PDF?", "APPLY", "Đáp án")));

        uploadQuestions(LECTURER_2, "C:\\tai-lieu\\de thi.pdf", "application/pdf", pdf, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("de thi.pdf"))
                .andExpect(jsonPath("$.data.lesson.title").value("de thi"))
                .andExpect(jsonPath("$.data.topic.name").value("de thi"))
                .andExpect(jsonPath("$.data.rubrics", hasSize(0)))
                // Khong chon rubric: cau hoi chua gan rubric
                .andExpect(jsonPath("$.data.questions[0].rubricId").doesNotExist());

        LlmRequest request = llm.lastRequest();
        assertThat(request.attachments()).hasSize(1);
        assertThat(request.attachments().get(0).mimeType()).isEqualTo("application/pdf");
        assertThat(request.attachments().get(0).data()).isEqualTo(pdf);
    }

    @Test
    void importQuestionsPdf_cropsFigureOfEachQuestion() throws Exception {
        // Trang A4 (595 x 842 diem) co 1 anh 200 x 150 diem, mep tren cach 100, mep trai cach 100
        // -> thang 0 - 1000 cua trang: [ymin 119, xmin 168, ymax 297, xmax 504]
        byte[] pdf = pdfWithImage(100, 100, 200, 150);
        Map<String, Object> withFigure = question("Tính góc x.", "APPLY", "x = 55°", "kề bù");
        withFigure.put("figurePage", 1);
        // Khung AI uoc luong lech vai phan tram: van phai cat dung theo anh that
        withFigure.put("figureBox", List.of(130, 180, 310, 520));
        Map<String, Object> noFigure = question("Phát biểu dấu hiệu chia hết cho 2.", "REMEMBER", "Tận cùng chẵn");
        noFigure.put("figurePage", 0);
        noFigure.put("figureBox", List.of());
        Map<String, Object> wrongPage = question("Tính góc y.", "APPLY", "y = 60°");
        wrongPage.put("figurePage", 9);
        llm.willReturn(questionsJson("Toán 6", "Góc", withFigure, noFigure, wrongPage));

        JsonNode data = data(uploadQuestions(LECTURER_2, "goc.pdf", "application/pdf", pdf, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questions[0].hasFigure").value(true))
                .andExpect(jsonPath("$.data.questions[1].hasFigure").value(false))
                .andExpect(jsonPath("$.data.questions[2].hasFigure").value(false)));
        String figureQuestionId = data.get("questions").get(0).get("id").asText();
        String plainQuestionId = data.get("questions").get(1).get("id").asText();

        JsonNode figure = data(mockMvc.perform(auth(get(CONTENT + "/questions/" + figureQuestionId + "/figure"), LECTURER_2))
                .andExpect(status().isOk()));
        assertThat(figure.get("dataUrl").asText()).startsWith("data:image/png;base64,");
        // Anh rong 200/595 trang (+ le 2 x 0,6%), ve o 160 DPI: khoang 460 diem anh
        assertThat(figure.get("width").asInt()).isBetween(420, 500);
        assertThat(figure.get("height").asInt()).isBetween(320, 380);

        // Danh sach cau hoi cung bao cau nao co hinh
        mockMvc.perform(auth(get(CONTENT + "/questions").param("topicId", data.get("topic").get("id").asText()), LECTURER_2))
                .andExpect(jsonPath("$.data[0].hasFigure").value(true))
                .andExpect(jsonPath("$.data[1].hasFigure").value(false));

        expectError(mockMvc.perform(auth(get(CONTENT + "/questions/" + plainQuestionId + "/figure"), LECTURER_2)),
                404, "QUESTION_FIGURE_NOT_FOUND");
        // Giang vien khac khong xem duoc
        expectError(mockMvc.perform(auth(get(CONTENT + "/questions/" + figureQuestionId + "/figure"), LECTURER_1)),
                404, "QUESTION_NOT_FOUND");
    }

    private byte[] pdfWithImage(float left, float top, float width, float height) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            document.addPage(page);
            var picture = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = picture.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, 400, 300);
            graphics.setColor(java.awt.Color.RED);
            graphics.drawLine(0, 299, 399, 0);
            graphics.dispose();
            var image = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, picture);
            try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                float pageHeight = page.getMediaBox().getHeight();
                stream.drawImage(image, left, pageHeight - top - height, width, height);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void import_rejectsBadFilesAndAiProblemsWithoutSavingAnything() throws Exception {
        int lessonsBefore = lessonCount(ADMIN);
        int rubricsBefore = idsOf(ADMIN, "/rubrics").size();
        byte[] text = "Câu 1: ...".getBytes(StandardCharsets.UTF_8);

        expectError(uploadQuestions(LECTURER_2, "de.doc", "application/msword", text, null), 400, "IMPORT_FILE_INVALID");
        expectError(uploadQuestions(LECTURER_2, "de.exe", "application/octet-stream", text, null), 400, "IMPORT_FILE_INVALID");
        expectError(uploadQuestions(LECTURER_2, "khong-phai.pdf", "application/pdf", text, null), 400, "IMPORT_FILE_INVALID");
        expectError(uploadQuestions(LECTURER_2, "hong.docx", DOCX_TYPE, text, null), 400, "IMPORT_FILE_INVALID");
        expectError(uploadQuestions(LECTURER_2, "rong.txt", "text/plain", new byte[0], null), 400, "IMPORT_FILE_INVALID");
        expectError(uploadQuestions(LECTURER_2, "trang.txt", "text/plain", "   \n ".getBytes(StandardCharsets.UTF_8), null),
                400, "IMPORT_FILE_INVALID");
        expectError(uploadRubrics(LECTURER_2, "de.doc", "application/msword", text), 400, "IMPORT_FILE_INVALID");
        expectError(mockMvc.perform(auth(multipart(CONTENT + "/imports/questions"), LECTURER_2)), 400, "VALIDATION_ERROR");
        expectError(mockMvc.perform(auth(multipart(CONTENT + "/imports/rubrics"), LECTURER_2)), 400, "VALIDATION_ERROR");
        // Cac file tren bi chan truoc khi goi AI
        assertThat(llm.calls()).isZero();

        // Sinh vien khong duoc gui
        expectError(uploadQuestions(student(1), "de.txt", "text/plain", text, null), 403, "ACCESS_DENIED");
        expectError(uploadRubrics(student(1), "de.txt", "text/plain", text), 403, "ACCESS_DENIED");

        // AI loi / AI khong thay cau hoi nao / AI tra khong phai JSON
        llm.willFail("Gemini trả lỗi HTTP 429");
        expectError(uploadQuestions(LECTURER_2, "de.txt", "text/plain", text, null), 502, "AI_REQUEST_FAILED");
        llm.willReturn(questionsJson("Toán", "Chủ đề"));
        expectError(uploadQuestions(LECTURER_2, "de.txt", "text/plain", text, null), 400, "IMPORT_NO_QUESTIONS");
        llm.willReturn("khong phai JSON");
        expectError(uploadRubrics(LECTURER_2, "de.txt", "text/plain", text), 502, "AI_REQUEST_FAILED");

        // Backend chua co GEMINI_API_KEY
        llm.setEnabled(false);
        expectError(uploadQuestions(LECTURER_2, "de.txt", "text/plain", text, null), 503, "AI_UNAVAILABLE");
        expectError(uploadRubrics(LECTURER_2, "de.txt", "text/plain", text), 503, "AI_UNAVAILABLE");

        assertThat(lessonCount(ADMIN)).isEqualTo(lessonsBefore);
        assertThat(idsOf(ADMIN, "/rubrics")).hasSize(rubricsBefore);
    }

    /* ========================= XEM / DUYET ========================= */

    @Test
    void lecturerSeesOnlyOwnContent_andApprovesQuestions() throws Exception {
        String rubricId = importRubric(LECTURER_2, "Rubric GV2");
        llm.willReturn(questionsJson("Bài của GV2", "Chủ đề GV2", question("Câu của GV2?", "UNDERSTAND", "Đáp án")));
        JsonNode imported = data(uploadQuestions(LECTURER_2, "gv2.txt", "text/plain", "noi dung".getBytes(StandardCharsets.UTF_8), rubricId)
                .andExpect(status().isCreated()));
        String topicId = imported.get("topic").get("id").asText();
        String questionId = imported.get("questions").get(0).get("id").asText();
        String lessonId = imported.get("lesson").get("id").asText();

        // Giang vien 2 thay bai hoc, chu de, rubric cua minh; admin cung thay; giang vien 1 thi khong
        assertThat(idsOf(LECTURER_2, "/lessons")).contains(lessonId);
        assertThat(idsOf(ADMIN, "/lessons")).contains(lessonId);
        assertThat(idsOf(LECTURER_1, "/lessons")).doesNotContain(lessonId);
        assertThat(idsOf(LECTURER_2, "/topics")).contains(topicId);
        assertThat(idsOf(LECTURER_1, "/topics")).doesNotContain(topicId);
        assertThat(idsOf(LECTURER_2, "/rubrics")).contains(rubricId);
        assertThat(idsOf(LECTURER_1, "/rubrics")).doesNotContain(rubricId);
        assertThat(idsOf(LECTURER_1, "/questions")).doesNotContain(questionId);
        mockMvc.perform(auth(get(CONTENT + "/lessons"), LECTURER_2))
                .andExpect(jsonPath("$.data[0].id").value(lessonId))
                .andExpect(jsonPath("$.data[0].topicCount").value(1))
                .andExpect(jsonPath("$.data[0].questionCount").value(1));
        mockMvc.perform(auth(get(CONTENT + "/topics").param("lessonId", lessonId), LECTURER_2))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].questionCount").value(1));
        expectError(mockMvc.perform(auth(get(CONTENT + "/questions"), student(1))), 403, "ACCESS_DENIED");

        // Duyet: giang vien khac khong duyet duoc, trang thai la bi tu choi
        expectError(changeStatus(LECTURER_1, questionId, "APPROVED", null), 404, "QUESTION_NOT_FOUND");
        expectError(changeStatus(LECTURER_2, questionId, "XYZ", null), 400, "VALIDATION_ERROR");
        expectError(changeStatus(LECTURER_2, "QS0000000000", "APPROVED", null), 404, "QUESTION_NOT_FOUND");
        changeStatus(LECTURER_2, questionId, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        changeStatus(ADMIN, questionId, "ARCHIVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void approve_requiresRubric() throws Exception {
        // Gui cau hoi ma khong chon rubric: cau hoi nhap chua gan rubric nen chua duyet duoc (BR-BANK-001)
        llm.willReturn(questionsJson("Bài không rubric", "Chủ đề", question("Câu chưa có rubric?", "APPLY", "")));
        String questionId = data(uploadQuestions(LECTURER_2, "a.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8), null))
                .get("questions").get(0).get("id").asText();
        expectError(changeStatus(LECTURER_2, questionId, "APPROVED", null), 409, "QUESTION_RUBRIC_REQUIRED");

        // Gan rubric cua minh khi duyet thi duoc; rubric khong ton tai thi bao loi
        String rubricId = importRubric(LECTURER_2, "Rubric để gán");
        expectError(changeStatus(LECTURER_2, questionId, "APPROVED", "RB0000000000"), 404, "RUBRIC_NOT_FOUND");
        changeStatus(LECTURER_2, questionId, "APPROVED", rubricId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.rubricId").value(rubricId))
                .andExpect(jsonPath("$.data.rubricName").value("Rubric để gán"));
    }

    /* ========================= DUNG DE THI ========================= */

    @Test
    void newSessionUsesNewestApprovedQuestionsOfItsOwner() throws Exception {
        String rubricId = importRubric(LECTURER_2, "Rubric");
        llm.willReturn(questionsJson("Bộ đề mới nhất", "Chủ đề mới nhất",
                question("Câu mới số 1?", "REMEMBER", "Đáp án 1"),
                question("Câu mới số 2?", "REMEMBER", "Đáp án 2"),
                question("Câu mới còn nháp?", "REMEMBER", "Đáp án 3")));
        JsonNode imported = data(uploadQuestions(LECTURER_2, "moi.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8), rubricId));
        changeStatus(LECTURER_2, imported.get("questions").get(0).get("id").asText(), "APPROVED", null).andExpect(status().isOk());
        changeStatus(LECTURER_2, imported.get("questions").get(1).get("id").asText(), "APPROVED", null).andExpect(status().isOk());

        // Phien cua giang vien 2: lay 2 cau vua duyet truoc (cau con nhap khong duoc lay), roi moi den bo cau hoi cu hon
        String attemptId = joinNewSession(LECTURER_2, student(3));
        JsonNode first = data(startInterview(student(3), attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionText").value("Câu mới số 1?"))
                .andExpect(jsonPath("$.data.questionNo").value(1)));
        skip(student(3), attemptId, first.get("exchangeId").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionText").value("Câu mới số 2?"));
    }

    /* ========================= MANUAL CRUD ========================= */

    @Test
    void manualCrudLessonsAndTopics() throws Exception {
        // 1. Create Lesson
        Map<String, Object> lessonReq = Map.of(
                "title", "Bài học thủ công",
                "description", "Mô tả bài học"
        );
        JsonNode lesson = data(mockMvc.perform(json(auth(post(CONTENT + "/lessons"), LECTURER_2), lessonReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Bài học thủ công"))
                .andExpect(jsonPath("$.data.topicCount").value(0)));
        String lessonId = lesson.get("id").asText();

        // 2. Update Lesson
        Map<String, Object> updateLessonReq = Map.of(
                "title", "Bài học đã sửa",
                "description", "Mô tả mới"
        );
        mockMvc.perform(json(auth(put(CONTENT + "/lessons/" + lessonId), LECTURER_2), updateLessonReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Bài học đã sửa"));

        // 3. Create Topic
        Map<String, Object> topicReq = Map.of(
                "lessonId", lessonId,
                "name", "Chủ đề 1",
                "description", "Mô tả chủ đề"
        );
        JsonNode topic = data(mockMvc.perform(json(auth(post(CONTENT + "/topics"), LECTURER_2), topicReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Chủ đề 1"))
                .andExpect(jsonPath("$.data.lessonId").value(lessonId)));
        String topicId = topic.get("id").asText();

        // 4. Update Topic
        Map<String, Object> updateTopicReq = Map.of(
                "name", "Chủ đề 1 đã sửa",
                "description", "Mô tả chủ đề mới"
        );
        mockMvc.perform(json(auth(put(CONTENT + "/topics/" + topicId), LECTURER_2), updateTopicReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Chủ đề 1 đã sửa"));

        // Lesson cannot be deleted while containing topics
        expectError(mockMvc.perform(auth(delete(CONTENT + "/lessons/" + lessonId), LECTURER_2)), 409, "DATA_CONFLICT");

        // 5. Delete Topic
        mockMvc.perform(auth(delete(CONTENT + "/topics/" + topicId), LECTURER_2))
                .andExpect(status().isOk());

        // 6. Delete Lesson
        mockMvc.perform(auth(delete(CONTENT + "/lessons/" + lessonId), LECTURER_2))
                .andExpect(status().isOk());
    }

    @Test
    void manualCrudRubricsAndCriteria() throws Exception {
        // Invalid weights sum != 100 -> RUBRIC_WEIGHT_INVALID
        Map<String, Object> badRubric = Map.of(
                "name", "Rubric sai weight",
                "criteria", List.of(
                        Map.of("name", "C1", "description", "Mô tả", "weightPercent", 40.0),
                        Map.of("name", "C2", "description", "Mô tả", "weightPercent", 40.0)
                )
        );
        expectError(mockMvc.perform(json(auth(post(CONTENT + "/rubrics"), LECTURER_2), badRubric)),
                400, "RUBRIC_WEIGHT_INVALID");

        // Valid weights sum == 100
        Map<String, Object> validRubric = Map.of(
                "name", "Rubric chuẩn",
                "criteria", List.of(
                        Map.of("name", "Tiêu chí 1", "description", "Mô tả 1", "weightPercent", 60.0),
                        Map.of("name", "Tiêu chí 2", "description", "Mô tả 2", "weightPercent", 40.0)
                )
        );
        JsonNode rubric = data(mockMvc.perform(json(auth(post(CONTENT + "/rubrics"), LECTURER_2), validRubric))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Rubric chuẩn"))
                .andExpect(jsonPath("$.data.criteria", hasSize(2))));
        String rubricId = rubric.get("id").asText();

        // Update rubric
        Map<String, Object> updateRubric = Map.of(
                "name", "Rubric đã cập nhật",
                "criteria", List.of(
                        Map.of("name", "Tiêu chí A", "description", "Mô tả A", "weightPercent", 100.0)
                )
        );
        mockMvc.perform(json(auth(put(CONTENT + "/rubrics/" + rubricId), LECTURER_2), updateRubric))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Rubric đã cập nhật"))
                .andExpect(jsonPath("$.data.criteria", hasSize(1)));

        // Delete rubric
        mockMvc.perform(auth(delete(CONTENT + "/rubrics/" + rubricId), LECTURER_2))
                .andExpect(status().isOk());
    }

    @Test
    void manualCrudQuestions() throws Exception {
        // Create lesson, topic, rubric
        JsonNode lesson = data(mockMvc.perform(json(auth(post(CONTENT + "/lessons"), LECTURER_2),
                Map.of("title", "Bài học hỏi đáp"))).andExpect(status().isCreated()));
        String lessonId = lesson.get("id").asText();

        JsonNode topic = data(mockMvc.perform(json(auth(post(CONTENT + "/topics"), LECTURER_2),
                Map.of("lessonId", lessonId, "name", "Chủ đề hỏi đáp"))).andExpect(status().isCreated()));
        String topicId = topic.get("id").asText();

        JsonNode rubric = data(mockMvc.perform(json(auth(post(CONTENT + "/rubrics"), LECTURER_2),
                Map.of("name", "Rubric câu hỏi", "criteria", List.of(
                        Map.of("name", "T1", "description", "Mô tả", "weightPercent", 100.0)
                )))).andExpect(status().isCreated()));
        String rubricId = rubric.get("id").asText();

        // 1. Create draft question
        Map<String, Object> questionReq = Map.of(
                "topicId", topicId,
                "content", "OOP là gì?",
                "bloomLevel", "UNDERSTAND",
                "modelAnswer", "Lập trình hướng đối tượng",
                "expectedKeywords", List.of("đối tượng", "lớp")
        );
        JsonNode created = data(mockMvc.perform(json(auth(post(CONTENT + "/questions"), LECTURER_2), questionReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.bloomLevel").value("UNDERSTAND"))
                .andExpect(jsonPath("$.data.topicId").value(topicId)));
        String questionId = created.get("id").asText();

        // 2. Update question to APPROVED with rubric
        Map<String, Object> updateReq = Map.of(
                "content", "OOP là gì? Cho ví dụ.",
                "bloomLevel", "APPLY",
                "rubricId", rubricId,
                "status", "APPROVED"
        );
        mockMvc.perform(json(auth(put(CONTENT + "/questions/" + questionId), LECTURER_2), updateReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.bloomLevel").value("APPLY"))
                .andExpect(jsonPath("$.data.rubricId").value(rubricId));

        // Cannot delete topic while containing question
        expectError(mockMvc.perform(auth(delete(CONTENT + "/topics/" + topicId), LECTURER_2)), 409, "DATA_CONFLICT");

        // Cannot delete rubric while used by question
        expectError(mockMvc.perform(auth(delete(CONTENT + "/rubrics/" + rubricId), LECTURER_2)), 409, "DATA_CONFLICT");

        // 3. Delete question
        mockMvc.perform(auth(delete(CONTENT + "/questions/" + questionId), LECTURER_2))
                .andExpect(status().isOk());

        // Now topic, rubric, lesson can be deleted
        mockMvc.perform(auth(delete(CONTENT + "/topics/" + topicId), LECTURER_2)).andExpect(status().isOk());
        mockMvc.perform(auth(delete(CONTENT + "/rubrics/" + rubricId), LECTURER_2)).andExpect(status().isOk());
        mockMvc.perform(auth(delete(CONTENT + "/lessons/" + lessonId), LECTURER_2)).andExpect(status().isOk());
    }

    /* ========================= HELPER ========================= */


    private ResultActions uploadQuestions(String email, String fileName, String contentType, byte[] bytes,
                                          String rubricId) throws Exception {
        var request = multipart(CONTENT + "/imports/questions").file(new MockMultipartFile("file", fileName, contentType, bytes));
        if (rubricId != null) {
            request.param("rubricId", rubricId);
        }
        return mockMvc.perform(auth(request, email));
    }

    private ResultActions uploadRubrics(String email, String fileName, String contentType, byte[] bytes) throws Exception {
        return mockMvc.perform(auth(multipart(CONTENT + "/imports/rubrics")
                .file(new MockMultipartFile("file", fileName, contentType, bytes)), email));
    }

    /** Gui 1 file rubric (2 tieu chi 70 / 30) va tra ve id rubric vua tao. */
    private String importRubric(String email, String name) throws Exception {
        llm.willReturn(rubricsJson(rubric(name, criterion("Tiêu chí A", 70), criterion("Tiêu chí B", 30))));
        return data(uploadRubrics(email, "rubric.txt", "text/plain", "tieu chi".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())).get("rubrics").get(0).get("id").asText();
    }

    private ResultActions changeStatus(String email, String questionId, String status, String rubricId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        if (rubricId != null) {
            body.put("rubricId", rubricId);
        }
        return mockMvc.perform(json(auth(put(CONTENT + "/questions/" + questionId + "/status"), email), body));
    }

    private List<String> idsOf(String email, String path) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : data(mockMvc.perform(auth(get(CONTENT + path), email)).andExpect(status().isOk()))) {
            ids.add(item.get("id").asText());
        }
        return ids;
    }

    private int lessonCount(String email) throws Exception {
        return idsOf(email, "/lessons").size();
    }

    /** File .docx toi thieu: 1 file zip co word/document.xml, moi dong la 1 doan van. */
    private byte[] docx(String... paragraphs) throws Exception {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for (String paragraph : paragraphs) {
            xml.append("<w:p><w:r><w:t>").append(paragraph).append("</w:t></w:r></w:p>");
        }
        xml.append("</w:body></w:document>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private Map<String, Object> criterion(String name, double weight) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("name", name);
        criterion.put("description", "Mô tả " + name);
        criterion.put("weightPercent", weight);
        return criterion;
    }

    @SafeVarargs
    private Map<String, Object> rubric(String name, Map<String, Object>... criteria) {
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("name", name);
        rubric.put("description", "");
        rubric.put("criteria", List.of(criteria));
        return rubric;
    }

    private Map<String, Object> question(String content, String bloomLevel, String modelAnswer, String... keywords) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("content", content);
        question.put("bloomLevel", bloomLevel);
        question.put("modelAnswer", modelAnswer);
        question.put("expectedKeywords", List.of(keywords));
        return question;
    }

    @SafeVarargs
    private String questionsJson(String lessonTitle, String topicName, Map<String, Object>... questions) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("lessonTitle", lessonTitle);
        json.put("topicName", topicName);
        json.put("questions", List.of(questions));
        return objectMapper.writeValueAsString(json);
    }

    @SafeVarargs
    private String rubricsJson(Map<String, Object>... rubrics) throws Exception {
        return objectMapper.writeValueAsString(Map.of("rubrics", List.of(rubrics)));
    }
}
