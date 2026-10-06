package com.aives.modules.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.modules.exam.enums.AttemptStatus;
import com.aives.modules.grading.repository.GradingAttemptRepository;
import com.aives.modules.grading.service.AttemptGradingService;
import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test cham diem va tham dinh tren H2 voi "AI" gia. Bo cham ngam bi tat trong test
 * (src/test/resources/config/application.yml) nen test tu goi AttemptGradingService.
 * Phien thi cua LECTURER_1 dung 3 cau hoi mau, rubric mau co 3 tieu chi trong so 60 / 25 / 15.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AiTestBeans.class)
class GradingApiTest extends ApiTestSupport {

    private static final String GRADING = "/api/grading";
    private static final String MY_ATTEMPTS = "/api/student/attempts";
    private static final String MY_APPEALS = "/api/student/appeals";

    private static Instant nextBase = Instant.parse("2027-06-01T01:00:00Z");

    @Autowired
    private AttemptGradingService attemptGradingService;

    @Autowired
    private GradingAttemptRepository gradingAttemptRepository;

    @BeforeEach
    void reset() {
        clock.set(nextBase);
        nextBase = nextBase.plus(2, ChronoUnit.DAYS);
        llm.reset();
    }

    @Test
    void fullFlow_aiGradesThenLecturerReviewsAndPublishes() throws Exception {
        String student = student(4);
        String attemptId = takeExam(student);

        // Chua cham: giang vien chua thay trong danh sach tham dinh, sinh vien thay luot thi nhung khong co diem
        assertThat(attemptIds(mockMvc.perform(auth(get(GRADING + "/attempts"), LECTURER_1)))).doesNotContain(attemptId);
        assertThat(attemptOf(student, attemptId).get("resultStatus").asText()).isEqualTo("NONE");
        assertThat(gradingAttemptRepository.findIdsWaitingForGrading(AttemptStatus.COMPLETED, PageRequest.of(0, 500)))
                .contains(attemptId);

        // AI cham: chi cau 1 co tra loi nen chi goi AI 1 lan. Cau 2 (bo qua) va cau 3 (im lang) tu dong 0 diem.
        llm.willReturn(gradeJson(
                score(1, 8, "chỉ chia hết cho 1 và chính nó", "Nêu đúng định nghĩa"),
                score(2, 6, "đoạn này sinh viên không hề nói", "Có ví dụ nhưng chưa đủ"),
                score(3, 10, "", "Diễn đạt rõ")));
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();
        assertThat(llm.calls()).isEqualTo(1);
        assertThat(llm.lastRequest().longRunning()).isTrue();
        assertThat(llm.lastPrompt())
                .contains("Đúng kiến thức (trọng số 60%)")
                .contains("Sinh viên trả lời: <<<Số nguyên tố là số chỉ chia hết cho 1 và chính nó, ví dụ 2 3 5>>>");
        // Da cham roi thi khong cham lan nua
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isFalse();

        // Giang vien xem: 8 x 60% + 6 x 25% + 10 x 15% = 7.8; tong 3 cau = (7.8 + 0 + 0) / 3 = 2.6
        assertThat(attemptIds(mockMvc.perform(auth(get(GRADING + "/attempts"), LECTURER_1)))).contains(attemptId);
        JsonNode review = data(mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.attempt.totalAiScore").value(2.6))
                .andExpect(jsonPath("$.data.attempt.totalFinalScore").doesNotExist())
                .andExpect(jsonPath("$.data.attempt.studentEmail").value(student))
                .andExpect(jsonPath("$.data.attempt.studentName").isNotEmpty())
                .andExpect(jsonPath("$.data.questionGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[0].status").value("AI_DRAFT"))
                .andExpect(jsonPath("$.data.questionGrades[0].aiScore").value(7.8))
                .andExpect(jsonPath("$.data.questionGrades[0].maxScore").value(10.0))
                .andExpect(jsonPath("$.data.questionGrades[0].aiStrengths").value("Điểm mạnh"))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[0].criterionName").value("Đúng kiến thức"))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[0].aiScore").value(8.0))
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[0].evidenceQuote").value("chỉ chia hết cho 1 và chính nó"))
                // AI "bia" doan trich sinh vien khong noi: bi bo
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades[1].evidenceQuote").doesNotExist())
                .andExpect(jsonPath("$.data.questionGrades[0].exchanges", hasSize(1)))
                .andExpect(jsonPath("$.data.questionGrades[0].exchanges[0].aiDecisionReason").value("Giải thích của AI"))
                .andExpect(jsonPath("$.data.questionGrades[1].aiScore").value(0.0))
                .andExpect(jsonPath("$.data.questionGrades[1].status").value("AI_DRAFT"))
                .andExpect(jsonPath("$.data.questionGrades[1].criteriaGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[2].aiScore").value(0.0)));
        List<String> gradeIds = new ArrayList<>();
        review.get("questionGrades").forEach(grade -> gradeIds.add(grade.get("id").asText()));

        // Quyen: giang vien khac va sinh vien khong tham dinh duoc; sinh vien chua xem duoc ket qua
        expectError(mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_2)), 403, "ACCESS_DENIED");
        assertThat(attemptIds(mockMvc.perform(auth(get(GRADING + "/attempts"), LECTURER_2)))).doesNotContain(attemptId);
        assertThat(attemptIds(mockMvc.perform(auth(get(GRADING + "/attempts"), ADMIN)))).contains(attemptId);
        expectError(mockMvc.perform(auth(get(GRADING + "/attempts"), student)), 403, "ACCESS_DENIED");
        expectError(saveGrade(LECTURER_2, gradeIds.get(0), 8, null), 403, "ACCESS_DENIED");
        expectError(mockMvc.perform(auth(get(MY_ATTEMPTS + "/" + attemptId + "/result"), student)), 409, "RESULT_NOT_PUBLISHED");
        assertThat(attemptOf(student, attemptId).hasNonNull("totalAiScore")).isFalse();

        // Chua chot het thi chua cong bo duoc
        expectError(publish(LECTURER_1, attemptId), 409, "GRADES_NOT_APPROVED");

        // Chot diem: lech qua 2 diem so voi AI (7.8 -> 5) phai co ghi chu; diem ngoai thang bi tu choi
        expectError(saveGrade(LECTURER_1, gradeIds.get(0), 5, null), 400, "GRADE_NOTE_REQUIRED");
        expectError(saveGrade(LECTURER_1, gradeIds.get(0), 5, "abc"), 400, "GRADE_NOTE_REQUIRED");
        expectError(saveGrade(LECTURER_1, gradeIds.get(0), 11, "Điểm quá thang"), 400, "GRADE_SCORE_INVALID");
        expectError(saveGrade(LECTURER_1, gradeIds.get(0), -1, "Điểm âm"), 400, "VALIDATION_ERROR");
        expectError(saveGrade(LECTURER_1, "QG0000000000", 5, null), 404, "QUESTION_GRADE_NOT_FOUND");
        saveGrade(LECTURER_1, gradeIds.get(0), 5, "Ví dụ chưa đủ, trừ điểm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.finalScore").value(5.0))
                .andExpect(jsonPath("$.data.lecturerNote").value("Ví dụ chưa đủ, trừ điểm"))
                .andExpect(jsonPath("$.data.gradedByName").isNotEmpty())
                .andExpect(jsonPath("$.data.criteriaGrades", hasSize(3)));
        saveGrade(LECTURER_1, gradeIds.get(1), 0, null).andExpect(status().isOk());
        expectError(publish(LECTURER_1, attemptId), 409, "GRADES_NOT_APPROVED");
        // Lech 1.5 diem thi khong can ghi chu
        saveGrade(LECTURER_1, gradeIds.get(2), 1.5, "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lecturerNote").doesNotExist());

        // Cong bo: (5 + 0 + 1.5) / 3 = 2.17
        expectError(publish(LECTURER_2, attemptId), 403, "ACCESS_DENIED");
        publish(LECTURER_1, attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.totalFinalScore").value(2.17))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());
        // Da cong bo thi khong sua, khong cham lai, khong cong bo lan nua
        expectError(saveGrade(LECTURER_1, gradeIds.get(0), 9, null), 409, "RESULT_ALREADY_PUBLISHED");
        expectError(mockMvc.perform(auth(post(GRADING + "/attempts/" + attemptId + "/regrade"), LECTURER_1)), 409, "RESULT_ALREADY_PUBLISHED");
        expectError(publish(LECTURER_1, attemptId), 409, "RESULT_ALREADY_PUBLISHED");

        // Sinh vien xem ket qua cua minh, khong thay ly do quyet dinh cua AI; nguoi khac khong xem duoc
        JsonNode mine = attemptOf(student, attemptId);
        assertThat(mine.get("resultStatus").asText()).isEqualTo("PUBLISHED");
        assertThat(mine.get("totalFinalScore").asDouble()).isEqualTo(2.17);
        mockMvc.perform(auth(get(MY_ATTEMPTS + "/" + attemptId + "/result"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.totalFinalScore").value(2.17))
                .andExpect(jsonPath("$.data.questionGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[0].finalScore").value(5.0))
                .andExpect(jsonPath("$.data.questionGrades[0].exchanges[0].transcript").isNotEmpty())
                .andExpect(jsonPath("$.data.questionGrades[0].exchanges[0].aiDecisionReason").doesNotExist());
        expectError(mockMvc.perform(auth(get(MY_ATTEMPTS + "/" + attemptId + "/result"), student(5))), 404, "EXAM_ATTEMPT_NOT_FOUND");
        expectError(mockMvc.perform(auth(get(MY_ATTEMPTS), LECTURER_1)), 403, "ACCESS_DENIED");
    }

    @Test
    void aiFailure_marksGradingFailed_thenRegradeOrManualGrade() throws Exception {
        String student = student(5);
        String attemptId = takeExam(student);

        // AI loi o cau 1: cau do FAILED, ca luot thi GRADING_FAILED; cau 2, 3 van duoc 0 diem
        llm.willFail("Gemini trả lỗi HTTP 429");
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();
        JsonNode failed = data(mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("GRADING_FAILED"))
                .andExpect(jsonPath("$.data.questionGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.questionGrades[0].aiScore").doesNotExist())
                .andExpect(jsonPath("$.data.questionGrades[0].criteriaGrades", hasSize(0)))
                .andExpect(jsonPath("$.data.questionGrades[1].status").value("AI_DRAFT")));

        // Giang vien chot tay cau 2 truoc, roi bam cham lai: cau da chot duoc giu, cac cau con lai duoc cham lai
        saveGrade(LECTURER_1, failed.get("questionGrades").get(1).get("id").asText(), 0, null).andExpect(status().isOk());
        mockMvc.perform(auth(post(GRADING + "/attempts/" + attemptId + "/regrade"), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("NONE"));

        // Lan nay AI tra diem ngoai thang o lan 1 (bi goi lai), lan 2 hop le
        llm.willReturn(gradeJson(score(1, 12, "", "Sai thang"), score(2, 5, "", ""), score(3, 5, "", "")));
        llm.willReturn(gradeJson(score(1, 10, "", "Đúng"), score(2, 10, "", "Đủ"), score(3, 10, "", "Rõ")));
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();
        mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("PENDING_REVIEW"))
                // (10 + 0 + 0) / 3
                .andExpect(jsonPath("$.data.attempt.totalAiScore").value(3.33))
                .andExpect(jsonPath("$.data.questionGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[0].status").value("AI_DRAFT"))
                .andExpect(jsonPath("$.data.questionGrades[0].aiScore").value(10.0))
                .andExpect(jsonPath("$.data.questionGrades[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.questionGrades[2].status").value("AI_DRAFT"));
        // 2 lan goi AI cho cau 1 (lan dau sai thang diem); cau 3 khong tra loi nen khong goi
        assertThat(llm.calls()).isEqualTo(3);
    }

    @Test
    void overdueAttemptIsClosedAndCanBeGraded() throws Exception {
        String student = student(6);
        String attemptId = joinNewSession(LECTURER_1, student);
        JsonNode first = data(startInterview(student, attemptId));
        answer(student, attemptId, first.get("exchangeId").asText(), "Trả lời rồi bỏ đi").andExpect(status().isOk());

        // Sinh vien tat trinh duyet, het 60 phut: bo cham ngam se chot luot thi
        clock.advance(Duration.ofMinutes(61));
        assertThat(attemptGradingService.closeOverdueAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(attemptOf(student, attemptId).get("status").asText()).isEqualTo("COMPLETED");

        llm.willReturn(gradeJson(score(1, 4, "", ""), score(2, 4, "", ""), score(3, 4, "", "")));
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();
        mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("PENDING_REVIEW"))
                // Cau 2, 3 sinh vien chua toi: van co diem (0) de tong diem tinh tren ca 3 cau
                .andExpect(jsonPath("$.data.questionGrades", hasSize(3)))
                .andExpect(jsonPath("$.data.questionGrades[0].aiScore").value(4.0))
                .andExpect(jsonPath("$.data.questionGrades[2].aiScore").value(0.0))
                .andExpect(jsonPath("$.data.questionGrades[2].exchanges", hasSize(0)));
    }

    @Test
    void appeal_studentSubmitsThenLecturerResolves() throws Exception {
        String student = student(7);
        String attemptId = takeExam(student);
        llm.willReturn(gradeJson(score(1, 8, "", ""), score(2, 8, "", ""), score(3, 8, "", "")));
        assertThat(attemptGradingService.gradeAttempt(attemptId)).isTrue();
        List<String> gradeIds = new ArrayList<>();
        data(mockMvc.perform(auth(get(GRADING + "/attempts/" + attemptId), LECTURER_1)).andExpect(status().isOk()))
                .get("questionGrades").forEach(grade -> gradeIds.add(grade.get("id").asText()));
        saveGrade(LECTURER_1, gradeIds.get(0), 8, null).andExpect(status().isOk());
        saveGrade(LECTURER_1, gradeIds.get(1), 0, null).andExpect(status().isOk());
        saveGrade(LECTURER_1, gradeIds.get(2), 0, null).andExpect(status().isOk());
        String reason = "Em đã nêu đúng định nghĩa và có ví dụ";

        // Chua cong bo thi chua phuc khao duoc
        expectError(submitAppeal(student, gradeIds.get(0), reason), 409, "RESULT_NOT_PUBLISHED");
        publish(LECTURER_1, attemptId).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalFinalScore").value(2.67));

        // Gui don: ly do qua ngan, diem cua nguoi khac, diem khong ton tai deu bi tu choi
        expectError(submitAppeal(student, gradeIds.get(0), "ngắn quá"), 400, "VALIDATION_ERROR");
        expectError(submitAppeal(student(6), gradeIds.get(0), reason), 404, "QUESTION_GRADE_NOT_FOUND");
        expectError(submitAppeal(student, "QG0000000000", reason), 404, "QUESTION_GRADE_NOT_FOUND");
        expectError(submitAppeal(LECTURER_1, gradeIds.get(0), reason), 403, "ACCESS_DENIED");
        String appealId = data(submitAppeal(student, gradeIds.get(0), "  " + reason + "  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value(reason))
                .andExpect(jsonPath("$.data.scoreBefore").value(8.0))
                .andExpect(jsonPath("$.data.scoreAfter").doesNotExist())
                .andExpect(jsonPath("$.data.attemptId").value(attemptId))
                .andExpect(jsonPath("$.data.questionText").isNotEmpty())
                .andExpect(jsonPath("$.data.studentName").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())).get("id").asText();
        // 1 cau chi co 1 don dang cho xu ly
        expectError(submitAppeal(student, gradeIds.get(0), reason), 409, "APPEAL_ALREADY_PENDING");

        // Tai lai trang: don van con, va hien ngay tren cau hoi trong ket qua
        assertThat(appealIds(mockMvc.perform(auth(get(MY_APPEALS), student)))).containsExactly(appealId);
        assertThat(appealIds(mockMvc.perform(auth(get(MY_APPEALS), student(6))))).doesNotContain(appealId);
        mockMvc.perform(auth(get(MY_ATTEMPTS + "/" + attemptId + "/result"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.id").value(appealId))
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.status").value("PENDING"))
                .andExpect(jsonPath("$.data.questionGrades[1].appeal").doesNotExist());

        // Giang vien tao phien thi va admin thay don; giang vien khac va sinh vien thi khong
        assertThat(appealIds(mockMvc.perform(auth(get(GRADING + "/appeals"), LECTURER_1)))).contains(appealId);
        assertThat(appealIds(mockMvc.perform(auth(get(GRADING + "/appeals"), ADMIN)))).contains(appealId);
        assertThat(appealIds(mockMvc.perform(auth(get(GRADING + "/appeals"), LECTURER_2)))).doesNotContain(appealId);
        expectError(mockMvc.perform(auth(get(GRADING + "/appeals"), student)), 403, "ACCESS_DENIED");
        expectError(resolveAppeal(LECTURER_2, appealId, "ACCEPTED", 9.5, "Đồng ý"), 403, "ACCESS_DENIED");
        expectError(resolveAppeal(student, appealId, "ACCEPTED", 9.5, "Đồng ý"), 403, "ACCESS_DENIED");

        // Xu ly: thieu phan hoi, quyet dinh la, thieu diem moi, diem ngoai thang deu bi tu choi
        expectError(resolveAppeal(LECTURER_1, appealId, "ACCEPTED", 9.5, " "), 400, "VALIDATION_ERROR");
        expectError(resolveAppeal(LECTURER_1, appealId, "MAYBE", 9.5, "Đồng ý"), 400, "VALIDATION_ERROR");
        expectError(resolveAppeal(LECTURER_1, appealId, "ACCEPTED", null, "Đồng ý"), 400, "GRADE_SCORE_INVALID");
        expectError(resolveAppeal(LECTURER_1, appealId, "ACCEPTED", 11.0, "Đồng ý"), 400, "GRADE_SCORE_INVALID");
        expectError(resolveAppeal(LECTURER_1, "GA0000000000", "ACCEPTED", 9.5, "Đồng ý"), 404, "APPEAL_NOT_FOUND");
        resolveAppeal(LECTURER_1, appealId, "ACCEPTED", 9.5, "Đồng ý, ví dụ của em hợp lệ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.scoreBefore").value(8.0))
                .andExpect(jsonPath("$.data.scoreAfter").value(9.5))
                .andExpect(jsonPath("$.data.response").value("Đồng ý, ví dụ của em hợp lệ"))
                .andExpect(jsonPath("$.data.resolvedByName").isNotEmpty())
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());
        expectError(resolveAppeal(LECTURER_1, appealId, "REJECTED", null, "Đổi ý"), 409, "APPEAL_ALREADY_RESOLVED");

        // Diem cau hoi va tong diem cua luot thi doi theo: (9.5 + 0 + 0) / 3 = 3.17; ket qua van la da cong bo
        mockMvc.perform(auth(get(MY_ATTEMPTS + "/" + attemptId + "/result"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.resultStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.attempt.totalFinalScore").value(3.17))
                .andExpect(jsonPath("$.data.questionGrades[0].finalScore").value(9.5))
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.scoreAfter").value(9.5))
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.response").value("Đồng ý, ví dụ của em hợp lệ"));
        assertThat(attemptOf(student, attemptId).get("totalFinalScore").asDouble()).isEqualTo(3.17);

        // Don da xu ly xong thi gui duoc don moi cho cung cau hoi; bi tu choi thi diem giu nguyen
        String second = data(submitAppeal(student, gradeIds.get(0), "Em nghĩ câu này xứng đáng 10 điểm")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scoreBefore").value(9.5))).get("id").asText();
        resolveAppeal(LECTURER_1, second, "REJECTED", null, "Điểm hiện tại đã hợp lý")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.scoreAfter").value(9.5));
        mockMvc.perform(auth(get(MY_ATTEMPTS + "/" + attemptId + "/result"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt.totalFinalScore").value(3.17))
                .andExpect(jsonPath("$.data.questionGrades[0].finalScore").value(9.5))
                // Tren cau hoi hien don moi nhat
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.id").value(second))
                .andExpect(jsonPath("$.data.questionGrades[0].appeal.status").value("REJECTED"));
        assertThat(appealIds(mockMvc.perform(auth(get(MY_APPEALS), student)))).containsExactlyInAnyOrder(appealId, second);
    }

    @Test
    void attemptWithoutInterview_isHiddenFromStudentHistoryAndNeverGraded() throws Exception {
        String student = student(6);
        // Vao phong roi thoat, khong bam bat dau phong van; het gio thi luot thi duoc chot
        String attemptId = joinNewSession(LECTURER_1, student);
        clock.advance(Duration.ofMinutes(61));
        assertThat(attemptGradingService.closeOverdueAttempts()).isGreaterThanOrEqualTo(1);

        assertThat(attemptIds(mockMvc.perform(auth(get(MY_ATTEMPTS), student)))).doesNotContain(attemptId);
        assertThat(gradingAttemptRepository.findIdsWaitingForGrading(AttemptStatus.COMPLETED, PageRequest.of(0, 500)))
                .doesNotContain(attemptId);
        assertThat(attemptIds(mockMvc.perform(auth(get(GRADING + "/attempts"), LECTURER_1)))).doesNotContain(attemptId);
    }

    /* ========================= HELPER ========================= */

    /** Sinh vien thi 3 cau: tra loi cau 1 (AI chon NEXT), bo qua cau 2, im lang o cau 3. Tra ve attemptId da COMPLETED. */
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

    private ResultActions saveGrade(String email, String gradeId, double finalScore, String note) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("finalScore", finalScore);
        body.put("lecturerNote", note);
        return mockMvc.perform(json(auth(put(GRADING + "/question-grades/" + gradeId), email), body));
    }

    private ResultActions publish(String email, String attemptId) throws Exception {
        return mockMvc.perform(auth(post(GRADING + "/attempts/" + attemptId + "/publish"), email));
    }

    private ResultActions submitAppeal(String email, String gradeId, String reason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("questionGradeId", gradeId);
        body.put("reason", reason);
        return mockMvc.perform(json(auth(post(MY_APPEALS), email), body));
    }

    private ResultActions resolveAppeal(String email, String appealId, String decision, Double scoreAfter, String response)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", decision);
        body.put("scoreAfter", scoreAfter);
        body.put("response", response);
        return mockMvc.perform(json(auth(put(GRADING + "/appeals/" + appealId), email), body));
    }

    private List<String> appealIds(ResultActions actions) throws Exception {
        List<String> ids = new ArrayList<>();
        data(actions.andExpect(status().isOk())).forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private List<String> attemptIds(ResultActions actions) throws Exception {
        List<String> ids = new ArrayList<>();
        data(actions.andExpect(status().isOk())).forEach(item -> ids.add(item.get("attemptId").asText()));
        return ids;
    }

    /** Luot thi trong "lich su ca thi" cua sinh vien. */
    private JsonNode attemptOf(String student, String attemptId) throws Exception {
        for (JsonNode item : data(mockMvc.perform(auth(get(MY_ATTEMPTS), student)).andExpect(status().isOk()))) {
            if (item.get("attemptId").asText().equals(attemptId)) {
                return item;
            }
        }
        throw new AssertionError("Không thấy lượt thi " + attemptId + " trong lịch sử của sinh viên");
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
