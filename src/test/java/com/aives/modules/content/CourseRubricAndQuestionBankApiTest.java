package com.aives.modules.content;

import com.aives.common.dto.PageResponse;
import com.aives.modules.content.dto.ChapterResponse;
import com.aives.modules.content.dto.CourseRubricResponse;
import com.aives.modules.content.dto.CreateChapterQuestionRequest;
import com.aives.modules.content.dto.CreateChapterRequest;
import com.aives.modules.content.dto.DifficultyRatioConfig;
import com.aives.modules.content.dto.GenerateQuestionsRequest;
import com.aives.modules.content.dto.QuestionResponse;
import com.aives.modules.content.dto.RubricCriteriaDto;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;
import com.aives.modules.content.dto.UpdateQuestionRequest;
import com.aives.modules.content.entity.Chapter;
import com.aives.modules.content.entity.Course;
import com.aives.modules.content.entity.Material;
import com.aives.modules.content.enums.QuestionDifficulty;
import com.aives.modules.content.enums.QuestionStatus;
import com.aives.modules.content.repository.ChapterRepository;
import com.aives.modules.content.repository.CourseRepository;
import com.aives.modules.content.repository.MaterialRepository;
import com.aives.modules.content.service.CourseRubricService;
import com.aives.modules.content.service.QuestionBankService;
import com.aives.modules.content.service.RagQuestionGenerationService;
import com.aives.testsupport.ApiTestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.aives.testsupport.AiTestBeans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiTestBeans.class)
class CourseRubricAndQuestionBankApiTest extends ApiTestSupport {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private CourseRubricService courseRubricService;

    @Autowired
    private QuestionBankService questionBankService;

    @Autowired
    private RagQuestionGenerationService ragQuestionGenerationService;

    private String lecturerToken;
    private Course testCourse;
    private Chapter testChapter;

    @BeforeEach
    void setupCourseAndChapter() throws Exception {
        lecturerToken = token(LECTURER_1);

        testCourse = courseRepository.findByCourseCode("SWD392_TEST").orElseGet(() -> {
            Course c = Course.builder()
                    .courseCode("SWD392_TEST")
                    .name("Kiến trúc phần mềm kiểm thử")
                    .description("Môn học phục vụ unit test")
                    .build();
            c.setId("CRS_TEST_01");
            return courseRepository.save(c);
        });

        testChapter = chapterRepository.findByCourse_IdOrderByOrderNoAsc(testCourse.getId()).stream()
                .findFirst()
                .orElseGet(() -> {
                    Chapter ch = Chapter.builder()
                            .course(testCourse)
                            .name("Chương 1: Clean Architecture")
                            .orderNo(1)
                            .description("Tổng quan kiến trúc sạch")
                            .build();
                    ch.setId("CHAP_TEST_01");
                    return chapterRepository.save(ch);
                });
    }

    @Test
    void rubricLifecycle_sumMustBe100_andApproveGate() throws Exception {
        // 1. Thử cập nhật Rubric với tổng trọng số khác 100% -> phải ném lỗi 400 (BR-BANK-001)
        UpdateCourseRubricRequest invalidRubric = UpdateCourseRubricRequest.builder()
                .name("Rubric chưa đủ 100%")
                .description("Mô tả")
                .criteria(List.of(
                        RubricCriteriaDto.builder()
                                .name("Tiêu chí 1")
                                .description("Mô tả 1")
                                .weightPercent(new BigDecimal("60.00"))
                                .orderNo(1)
                                .build(),
                        RubricCriteriaDto.builder()
                                .name("Tiêu chí 2")
                                .description("Mô tả 2")
                                .weightPercent(new BigDecimal("30.00")) // Tổng 90% != 100%
                                .orderNo(2)
                                .build()
                ))
                .build();

        mockMvc.perform(json(put("/api/v1/courses/" + testCourse.getId() + "/rubric"), invalidRubric)
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RUBRIC_WEIGHT_INVALID"));

        // 2. Tạo câu hỏi thủ công trong chương
        CreateChapterQuestionRequest qReq = CreateChapterQuestionRequest.builder()
                .content("Giải thích nguyên lý Single Responsibility Principle (SRP)?")
                .difficulty(QuestionDifficulty.EASY)
                .modelAnswer("Mỗi class chỉ nên có đúng một lý do để thay đổi.")
                .expectedKeywords(List.of("single responsibility", "lý do thay đổi"))
                .build();

        String createQuestionRes = mockMvc.perform(json(post("/api/v1/chapters/" + testChapter.getId() + "/questions"), qReq)
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"))
                .andReturn().getResponse().getContentAsString();

        String questionId = objectMapper.readTree(createQuestionRes).path("data").path("id").asText();
        assertThat(questionId).isNotBlank();

        // 3. Thử duyệt câu hỏi khi môn học chưa có Rubric 100% -> Chặn 400 (BR-BANK-001 Gate)
        mockMvc.perform(patch("/api/v1/questions/" + questionId + "/approve")
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_RUBRIC_REQUIRED"));

        // 4. Cập nhật Rubric chuẩn đúng 100%
        UpdateCourseRubricRequest validRubric = UpdateCourseRubricRequest.builder()
                .name("Rubric chuẩn 100% môn Kiến trúc phần mềm")
                .description("Tiêu chí chuẩn hóa")
                .criteria(List.of(
                        RubricCriteriaDto.builder()
                                .name("Khái niệm & Định nghĩa")
                                .description("Nêu chuẩn xác định nghĩa")
                                .weightPercent(new BigDecimal("50.00"))
                                .orderNo(1)
                                .build(),
                        RubricCriteriaDto.builder()
                                .name("Vận dụng & Ví dụ thực tiễn")
                                .description("Áp dụng đúng vào dự án")
                                .weightPercent(new BigDecimal("50.00"))
                                .orderNo(2)
                                .build()
                ))
                .build();

        mockMvc.perform(json(put("/api/v1/courses/" + testCourse.getId() + "/rubric"), validRubric)
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criteria").isArray())
                .andExpect(jsonPath("$.data.isLocked").value(false));

        // 5. Thử duyệt lại câu hỏi sau khi Rubric đã đủ 100% -> Duyệt thành công APPROVED!
        mockMvc.perform(patch("/api/v1/questions/" + questionId + "/approve")
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 6. Kiểm tra xem danh sách câu hỏi ngân hàng chung có câu hỏi vừa duyệt
        mockMvc.perform(get("/api/v1/chapters/" + testChapter.getId() + "/questions?status=APPROVED")
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].status").value("APPROVED"));
    }

    @Test
    void ragQuestionGeneration_generatesQuestionsWithoutRubric() throws Exception {
        // Tạo material READY
        Material mat = Material.builder()
                .id("MAT_TEST_READY_01")
                .chapterId(testChapter.getId())
                .title("Slide Kiến trúc sạch.pdf")
                .fileName("Slide_CleanArch.pdf")
                .fileSize(1024L)
                .fileType("pdf")
                .gcsUri("gs://exam-slides-bucket-2026/Slide_CleanArch.pdf")
                .ragStatus("READY")
                .uploadedBy("LE0000000001")
                .lecturerId("LE0000000001")
                .build();
        materialRepository.save(mat);

        GenerateQuestionsRequest request = GenerateQuestionsRequest.builder()
                .materialIds(List.of(mat.getId()))
                .totalQuestions(3)
                .difficultyRatio(DifficultyRatioConfig.builder()
                        .easyPercent(34)
                        .mediumPercent(33)
                        .hardPercent(33)
                        .build())
                .build();

        mockMvc.perform(json(post("/api/v1/chapters/" + testChapter.getId() + "/questions/generate"), request)
                        .header("Authorization", "Bearer " + lecturerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].source").value("AI_GENERATED"));
    }
}
