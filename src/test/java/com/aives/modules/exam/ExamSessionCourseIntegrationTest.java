package com.aives.modules.exam;

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
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.service.CourseRubricService;
import com.aives.modules.content.service.QuestionBankService;
import com.aives.modules.exam.repository.VivaExamQuestionRepository;
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

/**
 * Test ca thi lien ket mon hoc, chon cau hoi tu ngan hang (co dinh / ngau nhien),
 * tu dong boc cau hoi khi sinh vien bat dau, va ke thua CourseRubric trong buoc phong van AI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiTestBeans.class)
class ExamSessionCourseIntegrationTest extends ApiTestSupport {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private CourseRubricService courseRubricService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private VivaExamQuestionRepository examQuestionRepository;

    private Course course;
    private Chapter chapter1;
    private Chapter chapter2;
    private QuestionResponse q1;
    private QuestionResponse q2;
    private QuestionResponse q3;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-01T08:00:00Z"));
        llm.reset();

        course = courseRepository.findByCourseCode("INT301").orElseGet(() -> {
            Course c = Course.builder()
                    .courseCode("INT301")
                    .name("Software Architecture & Design")
                    .description("Mon kien truc phan mem")
                    .build();
            c.setId("CRS_INT301");
            return courseRepository.save(c);
        });

        chapter1 = chapterRepository.findByCourse_IdOrderByOrderNoAsc(course.getId()).stream()
                .filter(ch -> "Chapter 1 - Architectural Patterns".equals(ch.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Chapter ch = Chapter.builder()
                            .course(course)
                            .name("Chapter 1 - Architectural Patterns")
                            .orderNo(1)
                            .build();
                    ch.setId("CHAP_INT_01");
                    return chapterRepository.save(ch);
                });

        chapter2 = chapterRepository.findByCourse_IdOrderByOrderNoAsc(course.getId()).stream()
                .filter(ch -> "Chapter 2 - Microservices Architecture".equals(ch.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Chapter ch = Chapter.builder()
                            .course(course)
                            .name("Chapter 2 - Microservices Architecture")
                            .orderNo(2)
                            .build();
                    ch.setId("CHAP_INT_02");
                    return chapterRepository.save(ch);
                });

        // Thiet lap Rubric 100% cho mon hoc
        UserPrincipal principal = new UserPrincipal("LE474781", LECTURER_1, "hash", RoleName.LECTURER, true, false);

        courseRubricService.updateRubric(course.getId(), UpdateCourseRubricRequest.builder()
                .name("Rubric INT301 chuan 100%")
                .description("Tieu chi danh gia kien truc")
                .criteria(List.of(
                        RubricCriteriaDto.builder()
                                .name("Kien thuc mau kien truc")
                                .description("Hieu ro MVC, Clean Architecture")
                                .weightPercent(new BigDecimal("50.00"))
                                .orderNo(1)
                                .build(),
                        RubricCriteriaDto.builder()
                                .name("Kha nang phan tich & bien luan")
                                .description("Giai thich trade-off ro rang")
                                .weightPercent(new BigDecimal("50.00"))
                                .orderNo(2)
                                .build()
                ))
                .build(), principal);

        // Tao va phe duyet 3 cau hoi thuoc 2 chuong
        q1 = questionBankService.createManualQuestion(chapter1.getId(), CreateChapterQuestionRequest.builder()
                .content("Trình bày ưu và nhược điểm của Clean Architecture?")
                .difficulty(QuestionDifficulty.MEDIUM)
                .modelAnswer("Tách biệt business logic khỏi framework...")
                .expectedKeywords(List.of("Clean Architecture", "Separation of Concerns"))
                .build(), principal);
        questionBankService.approveQuestion(q1.getId(), principal);

        q2 = questionBankService.createManualQuestion(chapter1.getId(), CreateChapterQuestionRequest.builder()
                .content("Khái niệm Bounded Context trong Domain-Driven Design là gì?")
                .difficulty(QuestionDifficulty.HARD)
                .modelAnswer("Ranh giới ngữ cảnh logic mà một domain model được áp dụng...")
                .expectedKeywords(List.of("Bounded Context", "Domain-Driven Design"))
                .build(), principal);
        questionBankService.approveQuestion(q2.getId(), principal);

        q3 = questionBankService.createManualQuestion(chapter2.getId(), CreateChapterQuestionRequest.builder()
                .content("Sự khác biệt giữa RESTful API và gRPC là gì?")
                .difficulty(QuestionDifficulty.EASY)
                .modelAnswer("gRPC dùng HTTP/2 và Protobuf nhị phân, còn REST thường dùng JSON qua HTTP/1.1...")
                .expectedKeywords(List.of("REST", "gRPC", "Protobuf"))
                .build(), principal);
        questionBankService.approveQuestion(q3.getId(), principal);
    }

    @Test
    @DisplayName("Tạo ca thi liên kết môn học, bốc câu hỏi ngẫu nhiên khi sinh viên bắt đầu")
    void createExamSessionWithCourse_picksQuestionsOnStartAndInheritsCourseRubric() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Ca thi Kien truc phan mem Giua ky");
        body.put("courseId", course.getId());
        body.put("startAt", start.toString());
        body.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body.put("durationMinutes", 45);
        body.put("questionCount", 2);

        // 1. Tao ca thi kem courseId
        JsonNode session = data(mockMvc.perform(json(auth(post(SESSIONS), LECTURER_1), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseId").value(course.getId()))
                .andExpect(jsonPath("$.data.courseName").value("Software Architecture & Design"))
                .andExpect(jsonPath("$.data.questionCount").value(2)));

        String examId = session.get("id").asText();
        String passcode = session.get("passcode").asText();

        // Truoc khi sinh vien vao, phien thi chua gan cau hoi co dinh
        assertThat(examQuestionRepository.countByExam_Id(examId)).isZero();

        // 2. Sinh vien join exam
        clock.set(start);
        String studentEmail = student(1);
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("examId", examId);
        join.put("passcode", passcode);
        String attemptId = data(mockMvc.perform(json(auth(post(SESSIONS + "/join"), studentEmail), join))
                .andExpect(status().isOk())).get("attemptId").asText();

        // 3. Sinh vien bat dau lam bai -> he thong tu dong boc 2 cau hoi APPROVED tu Course/Chapters!
        JsonNode interview = data(mockMvc.perform(auth(post(interviewUrl(attemptId) + "/start"), studentEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUESTION"))
                .andExpect(jsonPath("$.data.totalQuestions").value(2))
                .andExpect(jsonPath("$.data.rubricName").value("Rubric INT301 chuan 100%")));

        // Kiem tra da gan 2 cau hoi vao viva_exam_questions
        assertThat(examQuestionRepository.countByExam_Id(examId)).isEqualTo(2);

        // 4. Sinh vien tra loi -> he thong buildContext ke thua CourseRubric
        llm.willReturn(followUpJson("INCOMPLETE", "Bạn hãy phân tích thêm về dependency rule?"));
        JsonNode answerResponse = data(answer(studentEmail, attemptId, interview.get("exchangeId").asText(),
                "Clean Architecture có nhiều layer và dependency rule hướng vào trong")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("FOLLOW_UP")));

        assertThat(answerResponse.get("questionText").asText()).contains("dependency rule");
    }

    @Test
    @DisplayName("Tạo ca thi cấu hình cố định với danh sách questionIds")
    void createExamSessionWithFixedQuestions_populatesExamQuestionsImmediately() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Ca thi Kien truc cau hoi co dinh");
        body.put("courseId", course.getId());
        body.put("questionIds", List.of(q1.getId(), q3.getId()));
        body.put("startAt", start.toString());
        body.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body.put("durationMinutes", 30);

        JsonNode session = data(mockMvc.perform(json(auth(post(SESSIONS), LECTURER_1), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseId").value(course.getId()))
                .andExpect(jsonPath("$.data.questionCount").value(2))
                .andExpect(jsonPath("$.data.questions", hasSize(2))));

        String examId = session.get("id").asText();
        // Da duoc gan 2 cau hoi ngay tu khi tao
        assertThat(examQuestionRepository.countByExam_Id(examId)).isEqualTo(2);

        // Kiem tra chi tiet phien thi tra ve danh sach cau hoi
        mockMvc.perform(auth(get(SESSIONS + "/" + examId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions", hasSize(2)))
                .andExpect(jsonPath("$.data.questions[0].id").value(q1.getId()))
                .andExpect(jsonPath("$.data.questions[1].id").value(q3.getId()));
    }

    @Test
    @DisplayName("Tạo ca thi thất bại nếu môn học không tồn tại hoặc chưa có Rubric 100%")
    void createExamSessionWithCourse_validatesCourseAndRubric() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);

        // Mon hoc khong ton tai
        Map<String, Object> body1 = new LinkedHashMap<>();
        body1.put("title", "Ca thi loi");
        body1.put("courseId", "CRS_NOT_EXIST");
        body1.put("startAt", start.toString());
        body1.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body1.put("durationMinutes", 30);

        mockMvc.perform(json(auth(post(SESSIONS), LECTURER_1), body1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));

        // Mon hoc chua co Rubric 100%
        Course courseWithoutRubric = Course.builder()
                .courseCode("NO_RUBRIC_101")
                .name("Mon chua co rubric")
                .build();
        courseWithoutRubric.setId("CRS_NO_RB");
        courseRepository.save(courseWithoutRubric);

        Map<String, Object> body2 = new LinkedHashMap<>();
        body2.put("title", "Ca thi thieu rubric");
        body2.put("courseId", courseWithoutRubric.getId());
        body2.put("startAt", start.toString());
        body2.put("endAt", start.plus(3, ChronoUnit.HOURS).toString());
        body2.put("durationMinutes", 30);

        mockMvc.perform(json(auth(post(SESSIONS), LECTURER_1), body2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_RUBRIC_REQUIRED"));
    }
}
