package com.aives.modules.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.content.dto.CreateChapterQuestionRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricCriteriaDto;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.service.CourseRubricService;
import com.aives.modules.content.service.QuestionBankService;
import com.aives.modules.grading.service.AttemptGradingService;
import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiTestBeans.class)
class CourseRubricAttemptGradingTest extends ApiTestSupport {

    private static final String GRADING = "/api/grading";
    private static final String SESSIONS = "/api/exam-sessions";
    private static final String STUDENT_ATTEMPTS = "/api/student/attempts";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private CourseRubricService courseRubricService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private AttemptGradingService attemptGradingService;

    private UserPrincipal lecturerPrincipal;
    private Course course;
    private Chapter chapter;
    private QuestionResponse q1;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2027-08-01T08:00:00Z"));
        llm.reset();
        lecturerPrincipal = new UserPrincipal("LE474781", LECTURER_1, "hash", RoleName.LECTURER, true, false);

        course = courseRepository.findByCourseCode("PRJ301").orElseGet(() -> {
            Course c = Course.builder()
                    .courseCode("PRJ301")
                    .name("Java Web Project")
                    .description("Java Web Course")
                    .build();
            c.setId("CRS_PRJ301");
            return courseRepository.save(c);
        });

        chapter = chapterRepository.findByCourse_IdOrderByOrderNoAsc(course.getId()).stream()
                .filter(ch -> "Servlet & JSP".equals(ch.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Chapter ch = Chapter.builder()
                            .course(course)
                            .name("Servlet & JSP")
                            .orderNo(1)
                            .build();
                    ch.setId("CHAP_PRJ_01");
                    return chapterRepository.save(ch);
                });

        // 1. Thiet lap CourseRubric 100% (60% Kien thuc, 40% Trinh bay)
        courseRubricService.updateRubric(
                course.getId(),
                UpdateCourseRubricRequest.builder()
                        .name("Rubric Chuan Mon Java Web")
                        .description("Tieu chuan danh gia van dap")
                        .criteria(List.of(
                                RubricCriteriaDto.builder()
                                        .name("Kien thuc chuyen mon Servlet")
                                        .description("Nam vung Request-Response cycle")
                                        .weightPercent(new BigDecimal("60.00"))
                                        .orderNo(1)
                                        .build(),
                                RubricCriteriaDto.builder()
                                        .name("Ky nang trinh bay van dap")
                                        .description("Trinh bay mach lac ro rang")
                                        .weightPercent(new BigDecimal("40.00"))
                                        .orderNo(2)
                                        .build()
                        ))
                        .build(),
                lecturerPrincipal
        );

        // 2. Tao cau hoi trong Chapter khong co rubric rieng va phe duyet
        q1 = questionBankService.createManualQuestion(
                chapter.getId(),
                CreateChapterQuestionRequest.builder()
                        .content("Phan biet doGet va doPost trong HttpServlet?")
                        .difficulty(QuestionDifficulty.MEDIUM)
                        .modelAnswer("doGet xu ly GET request, tham so truyen qua URL query string. doPost xu ly POST request, payload truyen trong body.")
                        .expectedKeywords(List.of("GET", "POST", "query string", "request body"))
                        .build(),
                lecturerPrincipal
        );
        questionBankService.approveQuestion(q1.getId(), lecturerPrincipal);
    }

    @Test
    @DisplayName("End-to-end: Ca thi dung cau hoi ngan hang ke thua CourseRubric -> AI cham diem -> Giang vien sua criteria & chot -> Cong bo -> Sinh vien xem diem")
    void testEndToEndCourseRubricGradingAndScoreDelivery() throws Exception {
        // 3. Tao ca thi VivaExam gan voi courseId va cau hoi da APPROVED
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Map<String, Object> sessionBody = new LinkedHashMap<>();
        sessionBody.put("title", "Thi Van Dap Java Web PRJ301");
        sessionBody.put("description", "Ca thi cuoi ky");
        sessionBody.put("startAt", start.toString());
        sessionBody.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        sessionBody.put("durationMinutes", 45);
        sessionBody.put("courseId", course.getId());
        sessionBody.put("questionIds", List.of(q1.getId()));

        JsonNode session = data(mockMvc.perform(json(auth(post(SESSIONS), LECTURER_1), sessionBody))
                .andExpect(status().isCreated()));
        String examId = session.get("id").asText();

        // 4. Sinh vien vao phong thi va thi
        clock.set(start);
        String student = student(6);
        Map<String, Object> joinBody = new LinkedHashMap<>();
        joinBody.put("examId", examId);
        joinBody.put("passcode", session.get("passcode").asText());

        JsonNode joinResp = data(mockMvc.perform(json(auth(post(SESSIONS + "/join"), student), joinBody))
                .andExpect(status().isOk()));
        String attemptId = joinResp.get("attemptId").asText();

        // Start interview
        JsonNode turn1 = data(startInterview(student, attemptId).andExpect(status().isOk()));
        String exchangeId = turn1.get("exchangeId").asText();

        // Tra loi cau hoi
        answer(student, attemptId, exchangeId, "doGet lay du lieu qua query string tren URL con doPost gui du lieu an trong body request")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        llm.reset();

        // 5. AI cham diem: LLM tra ve JSON
        llm.willReturn(objectMapper.writeValueAsString(Map.of(
                "criteria", List.of(
                        Map.of("criterionNo", 1, "score", 9.0, "evidenceQuote", "doGet lay du lieu qua query string tren URL", "rationale", "Dung ban chat"),
                        Map.of("criterionNo", 2, "score", 8.0, "evidenceQuote", "gui du lieu an trong body request", "rationale", "Trinh bay tot")
                ),
                "strengths", "Hieu ro phuong thuc HTTP GET va POST",
                "weaknesses", "Chua neu vi du cu the ve bao mat",
                "feedback", "Can bo sung ve HTTPS va CSRF"
        )));

        // Run grading
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();
        assertThat(llm.calls()).isEqualTo(1);
        assertThat(llm.lastPrompt())
                .contains("Kien thuc chuyen mon Servlet (trọng số 60%)")
                .contains("Ky nang trinh bay van dap (trọng số 40%)");

        // 6. Giang vien xem bai nop:
        // Score = 9.0 * 60% + 8.0 * 40% = 5.4 + 3.2 = 8.6
        JsonNode review = data(mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.attempt.totalAiScore").value(8.6))
                .andExpect(jsonPath("$.data.questionGrades", hasSize(1)))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades", hasSize(2)))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[0].criterionName").value("Kien thuc chuyen mon Servlet"))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[0].aiScore").value(9.0))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[1].criterionName").value("Ky nang trinh bay van dap"))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[1].aiScore").value(8.0)));

        String questionGradeId = review.get("questionGrades").get(0).get("id").asText();
        String cg1Id = review.get("questionGrades").get(0).get("criteriaGrades").get(0).get("id").asText();
        String cg2Id = review.get("questionGrades").get(0).get("criteriaGrades").get(1).get("id").asText();

        // 7. Giang vien ghi de diem: cap nhat criteria finalScore va chot diem tong
        // Chot: criterion 1 = 9.5, criterion 2 = 8.5 -> finalScore = 9.1
        Map<String, Object> saveBody = new LinkedHashMap<>();
        saveBody.put("finalScore", 9.1);
        saveBody.put("lecturerNote", "Dong y voi AI va cong diem thuong cho cau tra loi ngan gon xuc tich");
        saveBody.put("criteriaScores", List.of(
                Map.of("criteriaGradeId", cg1Id, "finalScore", 9.5),
                Map.of("criteriaGradeId", cg2Id, "finalScore", 8.5)
        ));

        mockMvc.perform(json(auth(put(GRADING + "/question-grades/" + questionGradeId), LECTURER_1), saveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.finalScore").value(9.1))
                .andExpect(jsonPath("$.data.criteriaGrades[0].finalScore").value(9.5))
                .andExpect(jsonPath("$.data.criteriaGrades[1].finalScore").value(8.5));

        // 8. Giang vien cong bo ket qua (PUBLISHED)
        mockMvc.perform(auth(post(GRADING + "/attempts/" + attemptId + "/publish"), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.totalFinalScore").value(9.1));

        // 9. Sinh vien xem ket qua chi tiet tren ExamResult / StudentResults
        mockMvc.perform(auth(get(STUDENT_ATTEMPTS + "/" + attemptId + "/result"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.totalFinalScore").value(9.1))
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades", hasSize(2)))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[0].finalScore").value(9.5))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[1].finalScore").value(8.5))
                .andExpect(jsonPath("$.data.questionGrades[0].aiStrengths").value("Hieu ro phuong thuc HTTP GET va POST"));
    }
}
