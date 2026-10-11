package com.aives.modules.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Test API phien thi tren H2 (profile dev), dung tai khoan mau cua DataInitializer.
 * Dong ho duoc thay bang MutableClock de tua thoi gian (mo phien, het gio, het khoa).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ExamSessionApiTest {

    private static final String SESSIONS = "/api/exam-sessions";
    private static final String LECTURER_1 = "lecturer01@fe.edu.vn";
    private static final String LECTURER_2 = "lecturer02@fe.edu.vn";
    private static final String ADMIN = "admin@aives.local";

    // Moi test bat dau cach test truoc 2 ngay, de phien thi va bo dem nhap sai cua test cu khong anh huong
    private static Instant nextBase = Instant.parse("2026-10-05T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock clock;

    private final Map<String, String> tokens = new HashMap<>();

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(nextBase);
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            this.instant = value;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void resetClock() {
        clock.set(nextBase);
        nextBase = nextBase.plus(2, ChronoUnit.DAYS);
    }

    /* ========================= TAO PHIEN ========================= */

    @Test
    void create_returnsExamCodeAndPasscode() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        create(LECTURER_1, body("  Vấn đáp giữa kỳ  ", start, start.plus(3, ChronoUnit.HOURS), 60))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id", matchesPattern("^AIVES_EXAM_2026_\\d{6}$")))
                .andExpect(jsonPath("$.data.examCode").doesNotExist())
                .andExpect(jsonPath("$.data.passcode", matchesPattern("^\\d{6}$")))
                .andExpect(jsonPath("$.data.title").value("Vấn đáp giữa kỳ"))
                .andExpect(jsonPath("$.data.status").value("UPCOMING"))
                .andExpect(jsonPath("$.data.durationMinutes").value(60))
                // Khong gui maxAttempts thi mac dinh moi sinh vien chi duoc thi 1 lan
                .andExpect(jsonPath("$.data.maxAttempts").value(1))
                .andExpect(jsonPath("$.data.attemptCount").value(0))
                .andExpect(jsonPath("$.data.scheduleLocked").value(false))
                .andExpect(jsonPath("$.data.ownerName").isNotEmpty())
                .andExpect(jsonPath("$.data.startAt").value(start.toString()));
    }

    @Test
    void create_acceptsLocalOffsetAndStoresSameInstant() throws Exception {
        // 09:00 gio Viet Nam = 02:00 UTC
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Phiên theo giờ Việt Nam");
        body.put("startAt", "2026-12-01T09:00:00+07:00");
        body.put("endAt", "2026-12-01T11:00:00+07:00");
        body.put("durationMinutes", 30);
        clock.set(Instant.parse("2026-11-30T00:00:00Z"));
        create(LECTURER_1, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.startAt").value("2026-12-01T02:00:00Z"))
                .andExpect(jsonPath("$.data.endAt").value("2026-12-01T04:00:00Z"));
    }

    @Test
    void create_rejectsInvalidFields() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(3, ChronoUnit.HOURS);

        create(LECTURER_1, body("   ", start, end, 60))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.title").exists());

        create(LECTURER_1, body("a".repeat(201), start, end, 60))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.title").exists());

        create(LECTURER_1, body("Phiên", start, end, 4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.durationMinutes").exists());

        create(LECTURER_1, body("Phiên", start, end, 301))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.durationMinutes").exists());

        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("title", "Phiên");
        create(LECTURER_1, missing)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.startAt").exists())
                .andExpect(jsonPath("$.data.endAt").exists())
                .andExpect(jsonPath("$.data.durationMinutes").exists());

        // Thoi gian khong co mui gio -> khong doc duoc body
        Map<String, Object> noOffset = new LinkedHashMap<>();
        noOffset.put("title", "Phiên");
        noOffset.put("startAt", "2026-12-01T09:00:00");
        noOffset.put("endAt", "2026-12-01T11:00:00");
        noOffset.put("durationMinutes", 30);
        create(LECTURER_1, noOffset)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void create_rejectsInvalidSchedule() throws Exception {
        Instant now = clock.instant();
        Instant start = now.plus(1, ChronoUnit.HOURS);

        expectError(create(LECTURER_1, body("Phiên", start, start, 60)), 400, "EXAM_TIME_RANGE_INVALID");
        expectError(create(LECTURER_1, body("Phiên", start, start.minusSeconds(60), 60)), 400, "EXAM_TIME_RANGE_INVALID");
        expectError(create(LECTURER_1, body("Phiên", now.minus(5, ChronoUnit.MINUTES), start, 30)), 400, "EXAM_START_IN_PAST");
        expectError(create(LECTURER_1, body("Phiên", now.plus(366, ChronoUnit.DAYS), now.plus(367, ChronoUnit.DAYS), 30)),
                400, "EXAM_START_TOO_FAR");
        expectError(create(LECTURER_1, body("Phiên", start, start.plus(31, ChronoUnit.DAYS), 60)), 400, "EXAM_WINDOW_TOO_LONG");
        // Phien mo 30 phut nhung thoi luong 60 phut
        expectError(create(LECTURER_1, body("Phiên", start, start.plus(30, ChronoUnit.MINUTES), 60)),
                400, "EXAM_DURATION_EXCEEDS_WINDOW");

        // Bien: mo dung 30 ngay, thoi luong bang dung khoang mo, gio mo lech qua khu 30 giay -> hop le
        create(LECTURER_1, body("Phiên", start, start.plus(30, ChronoUnit.DAYS), 60)).andExpect(status().isCreated());
        create(LECTURER_1, body("Phiên", start, start.plus(60, ChronoUnit.MINUTES), 60)).andExpect(status().isCreated());
        create(LECTURER_1, body("Phiên", now.minusSeconds(30), start, 30)).andExpect(status().isCreated());
    }

    /* ========================= PHAN QUYEN ========================= */

    @Test
    void permissions() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Map<String, Object> body = body("Phiên của GV1", start, start.plus(2, ChronoUnit.HOURS), 30);
        String id = createAndGet(LECTURER_1, body).get("id").asText();

        // Chua dang nhap
        mockMvc.perform(get(SESSIONS)).andExpect(status().isUnauthorized());
        // Sinh vien khong duoc quan ly phien
        expectError(create(student(1), body), 403, "ACCESS_DENIED");
        mockMvc.perform(auth(get(SESSIONS), student(1))).andExpect(status().isForbidden());
        mockMvc.perform(auth(get(SESSIONS + "/" + id), student(1))).andExpect(status().isForbidden());
        // Giang vien khac khong xem / sua / xoa / tao lai ma duoc
        expectError(mockMvc.perform(auth(get(SESSIONS + "/" + id), LECTURER_2)), 403, "ACCESS_DENIED");
        expectError(mockMvc.perform(json(auth(put(SESSIONS + "/" + id), LECTURER_2), body)), 403, "ACCESS_DENIED");
        expectError(mockMvc.perform(auth(delete(SESSIONS + "/" + id), LECTURER_2)), 403, "ACCESS_DENIED");
        expectError(mockMvc.perform(auth(post(SESSIONS + "/" + id + "/regenerate-passcode"), LECTURER_2)), 403, "ACCESS_DENIED");
        // Giang vien va admin khong vao thi duoc
        expectError(join(LECTURER_1, "AIVES_EXAM_2026_000000", "000000"), 403, "ACCESS_DENIED");
        expectError(join(ADMIN, "AIVES_EXAM_2026_000000", "000000"), 403, "ACCESS_DENIED");

        // Chu phien va admin xem duoc
        mockMvc.perform(auth(get(SESSIONS + "/" + id), LECTURER_1)).andExpect(status().isOk());
        mockMvc.perform(auth(get(SESSIONS + "/" + id), ADMIN)).andExpect(status().isOk());
        // Khong ton tai
        expectError(mockMvc.perform(auth(get(SESSIONS + "/AIVES_EXAM_2020_000000"), ADMIN)), 404, "EXAM_SESSION_NOT_FOUND");

        // Danh sach: GV2 khong thay phien cua GV1, admin thay
        mockMvc.perform(auth(get(SESSIONS).param("keyword", "Phiên của GV1"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(auth(get(SESSIONS).param("keyword", "Phiên của GV1").param("status", "UPCOMING"), ADMIN))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')]", hasSize(1)));
    }

    /* ========================= SUA PHIEN ========================= */

    @Test
    void update_rules() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        JsonNode created = createAndGet(LECTURER_1, body("Phiên", start, end, 30));
        String url = SESSIONS + "/" + created.get("id").asText();

        // Chua mo: sua tu do, nhung van phai hop le
        Instant newStart = start.plus(30, ChronoUnit.MINUTES);
        mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên đã sửa", newStart, end, 45)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Phiên đã sửa"))
                .andExpect(jsonPath("$.data.durationMinutes").value(45))
                .andExpect(jsonPath("$.data.startAt").value(newStart.toString()))
                .andExpect(jsonPath("$.data.id").value(created.get("id").asText()));
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", end, newStart, 45))),
                400, "EXAM_TIME_RANGE_INVALID");
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", newStart, end, 200))),
                400, "EXAM_DURATION_EXCEEDS_WINDOW");

        // Phien da mo
        clock.set(newStart.plus(10, ChronoUnit.MINUTES));
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", newStart.plusSeconds(60), end, 45))),
                409, "EXAM_SESSION_LOCKED_FIELDS");
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", newStart, end, 60))),
                409, "EXAM_SESSION_LOCKED_FIELDS");
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", newStart, end.minusSeconds(60), 45))),
                409, "EXAM_END_CANNOT_SHORTEN");
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", newStart, newStart.plus(31, ChronoUnit.DAYS), 45))),
                400, "EXAM_WINDOW_TOO_LONG");
        Instant extended = end.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(json(auth(put(url), LECTURER_1), body("Tên mới khi đang mở", newStart, extended, 45)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONGOING"))
                .andExpect(jsonPath("$.data.scheduleLocked").value(true))
                .andExpect(jsonPath("$.data.title").value("Tên mới khi đang mở"))
                .andExpect(jsonPath("$.data.endAt").value(extended.toString()));

        // Phien da ket thuc
        clock.set(extended.plusSeconds(1));
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", newStart, extended.plus(1, ChronoUnit.HOURS), 45))),
                409, "EXAM_SESSION_ENDED");
        expectError(mockMvc.perform(auth(post(url + "/regenerate-passcode"), LECTURER_1)), 409, "EXAM_SESSION_ENDED");
    }

    /* ========================= VAO THI ========================= */

    @Test
    void join_validatesCodeAndPasscode() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        JsonNode session = createAndGet(LECTURER_1, body("Phiên", start, start.plus(2, ChronoUnit.HOURS), 60));
        String code = session.get("id").asText();
        String passcode = session.get("passcode").asText();
        String student = student(1);

        // Sai dinh dang
        expectError(join(student, "", passcode), 400, "VALIDATION_ERROR");
        expectError(join(student, code, ""), 400, "VALIDATION_ERROR");
        expectError(join(student, "EXAM_2026_049282", passcode), 400, "EXAM_CODE_INVALID_FORMAT");
        expectError(join(student, "AIVES_EXAM_26_049282", passcode), 400, "EXAM_CODE_INVALID_FORMAT");
        expectError(join(student, code + "1", passcode), 400, "EXAM_CODE_INVALID_FORMAT");
        expectError(join(student, code, "12345"), 400, "EXAM_PASSCODE_INVALID_FORMAT");
        expectError(join(student, code, "12a456"), 400, "EXAM_PASSCODE_INVALID_FORMAT");

        // Sai ma truy cap va ma phien khong ton tai: cung 1 loi
        expectError(join(student, code, wrong(passcode)), 400, "EXAM_JOIN_INVALID");
        expectError(join(student, "AIVES_EXAM_2020_000000", passcode), 400, "EXAM_JOIN_INVALID");

        // Dung ma nhung chua den gio
        expectError(join(student, code, passcode), 409, "EXAM_SESSION_NOT_STARTED");

        // Den gio: vao duoc, khong phan biet hoa thuong va khoang trang
        clock.set(start);
        join(student, "  " + code.toLowerCase() + " ", " " + passcode + " ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId", matchesPattern("^AT\\d{8}$")))
                .andExpect(jsonPath("$.data.examId").value(code))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(3600))
                .andExpect(jsonPath("$.data.passcode").doesNotExist());

        // Het gio mo phien: sinh vien chua vao lan nao khong vao duoc nua
        clock.set(start.plus(2, ChronoUnit.HOURS));
        expectError(join(student(2), code, passcode), 409, "EXAM_SESSION_ENDED");
    }

    @Test
    void join_resumeKeepsCountdownAndDeadlineIsCappedAtEnd() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        JsonNode session = createAndGet(LECTURER_1, body("Phiên", start, end, 60));
        String code = session.get("id").asText();
        String passcode = session.get("passcode").asText();

        // SV1 vao luc mo phien: du 60 phut
        clock.set(start);
        JsonNode first = data(join(student(1), code, passcode).andExpect(status().isOk()));
        String attemptId = first.get("attemptId").asText();
        assertThat(first.get("deadlineAt").asText()).isEqualTo(start.plus(60, ChronoUnit.MINUTES).toString());

        // 10 phut sau vao lai: cung luot thi, dong ho khong chay lai
        clock.advance(Duration.ofMinutes(10));
        join(student(1), code, passcode)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(attemptId))
                .andExpect(jsonPath("$.data.remainingSeconds").value(3000))
                .andExpect(jsonPath("$.data.deadlineAt").value(first.get("deadlineAt").asText()));
        mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingSeconds").value(3000))
                .andExpect(jsonPath("$.data.serverTime").value(clock.instant().toString()))
                .andExpect(jsonPath("$.data.title").value("Phiên"));

        // Luot thi cua nguoi khac / khong ton tai -> 404
        expectError(mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student(2))), 404, "EXAM_ATTEMPT_NOT_FOUND");
        expectError(mockMvc.perform(auth(get(SESSIONS + "/attempts/AT00000000"), student(1))), 404, "EXAM_ATTEMPT_NOT_FOUND");

        // SV2 vao khi con 20 phut la dong phien: chi duoc 20 phut
        clock.set(end.minus(20, ChronoUnit.MINUTES));
        join(student(2), code, passcode)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingSeconds").value(1200))
                .andExpect(jsonPath("$.data.deadlineAt").value(end.toString()));

        // Giang vien thay 2 luot thi
        mockMvc.perform(auth(get(SESSIONS + "/" + session.get("id").asText()), LECTURER_1))
                .andExpect(jsonPath("$.data.attemptCount").value(2));

        // SV1 da het 60 phut: xem luot thi thay COMPLETED, vao lai bi tu choi
        mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(0));
        expectError(join(student(1), code, passcode), 409, "EXAM_ATTEMPT_LIMIT_REACHED");
    }

    @Test
    void join_locksAfterFiveWrongAttempts() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        JsonNode session = createAndGet(LECTURER_1, body("Phiên", start, start.plus(2, ChronoUnit.HOURS), 60));
        String code = session.get("id").asText();
        String passcode = session.get("passcode").asText();
        String student = student(3);
        clock.set(start);

        for (int i = 0; i < 4; i++) {
            expectError(join(student, code, wrong(passcode)), 400, "EXAM_JOIN_INVALID");
        }
        // Lan sai thu 5: bi khoa, ke ca khi nhap dung
        expectError(join(student, code, wrong(passcode)), 429, "EXAM_JOIN_LOCKED");
        expectError(join(student, code, passcode), 429, "EXAM_JOIN_LOCKED");
        // Sinh vien khac khong bi anh huong
        join(student(4), code, passcode).andExpect(status().isOk());

        // 9 phut sau van khoa, qua 10 phut thi vao duoc
        clock.advance(Duration.ofMinutes(9));
        expectError(join(student, code, passcode), 429, "EXAM_JOIN_LOCKED");
        clock.advance(Duration.ofSeconds(61));
        join(student, code, passcode).andExpect(status().isOk());
    }

    @Test
    void regeneratePasscode_invalidatesOldOne() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        JsonNode session = createAndGet(LECTURER_1, body("Phiên", start, start.plus(2, ChronoUnit.HOURS), 60));
        String url = SESSIONS + "/" + session.get("id").asText();
        String code = session.get("id").asText();
        String oldPasscode = session.get("passcode").asText();
        clock.set(start);

        JsonNode updated = data(mockMvc.perform(auth(post(url + "/regenerate-passcode"), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passcode", matchesPattern("^\\d{6}$"))));
        String newPasscode = updated.get("passcode").asText();
        assertThat(newPasscode).isNotEqualTo(oldPasscode);
        assertThat(updated.get("id").asText()).isEqualTo(code);

        expectError(join(student(5), code, oldPasscode), 400, "EXAM_JOIN_INVALID");
        join(student(5), code, newPasscode).andExpect(status().isOk());
    }

    @Test
    void finish_endsAttemptEarlyAndBlocksRejoin() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        JsonNode session = createAndGet(LECTURER_1, body("Phiên", start, start.plus(2, ChronoUnit.HOURS), 60));
        String code = session.get("id").asText();
        String passcode = session.get("passcode").asText();
        clock.set(start);
        String attemptId = data(join(student(2), code, passcode)).get("attemptId").asText();
        String finishUrl = SESSIONS + "/attempts/" + attemptId + "/finish";

        // Dang thi thi chua co completedAt
        mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student(2)))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        // Nguoi khac / giang vien khong ket thuc ho duoc
        expectError(mockMvc.perform(auth(post(finishUrl), student(1))), 404, "EXAM_ATTEMPT_NOT_FOUND");
        expectError(mockMvc.perform(auth(post(finishUrl), LECTURER_1)), 403, "ACCESS_DENIED");

        // Ket thuc sau 15 phut
        clock.advance(Duration.ofMinutes(15));
        Instant finishedAt = clock.instant();
        mockMvc.perform(auth(post(finishUrl), student(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(0))
                .andExpect(jsonPath("$.data.completedAt").value(finishedAt.toString()));

        // Goi lai: giu nguyen thoi diem ket thuc lan dau
        clock.advance(Duration.ofMinutes(5));
        mockMvc.perform(auth(post(finishUrl), student(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").value(finishedAt.toString()));
        mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student(2)))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(0));

        // Da ket thuc thi khong vao lai duoc, du con thoi gian
        expectError(join(student(2), code, passcode), 409, "EXAM_ATTEMPT_LIMIT_REACHED");
    }

    /* ========================= GIOI HAN SO LUOT THI ========================= */

    @Test
    void create_rejectsInvalidMaxAttempts() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        for (int invalid : new int[] {0, -1, 11}) {
            Map<String, Object> body = body("Phiên", start, start.plus(2, ChronoUnit.HOURS), 30);
            body.put("maxAttempts", invalid);
            create(LECTURER_1, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.maxAttempts").isNotEmpty());
        }
    }

    @Test
    void join_allowsRetakeUntilMaxAttemptsIsUsedUp() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(5, ChronoUnit.HOURS);
        Map<String, Object> body = body("Phiên thi 2 lượt", start, end, 60);
        body.put("maxAttempts", 2);
        JsonNode session = createAndGet(LECTURER_1, body);
        assertThat(session.get("maxAttempts").asInt()).isEqualTo(2);
        String code = session.get("id").asText();
        String passcode = session.get("passcode").asText();

        // Luot 1
        clock.set(start);
        JsonNode first = data(join(student(3), code, passcode).andExpect(status().isOk()));
        assertThat(first.get("attemptNo").asInt()).isEqualTo(1);
        assertThat(first.get("maxAttempts").asInt()).isEqualTo(2);

        // Dang thi ma vao lai: van la luot 1, khong ton them luot
        clock.advance(Duration.ofMinutes(5));
        join(student(3), code, passcode)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptId").value(first.get("attemptId").asText()))
                .andExpect(jsonPath("$.data.attemptNo").value(1))
                .andExpect(jsonPath("$.data.remainingSeconds").value(3300));

        // Ket thuc luot 1 roi vao lai: tao luot 2, dong ho chay lai tu dau
        mockMvc.perform(auth(post(SESSIONS + "/attempts/" + first.get("attemptId").asText() + "/finish"), student(3)))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(5));
        JsonNode second = data(join(student(3), code, passcode).andExpect(status().isOk()));
        assertThat(second.get("attemptId").asText()).isNotEqualTo(first.get("attemptId").asText());
        assertThat(second.get("attemptNo").asInt()).isEqualTo(2);
        assertThat(second.get("remainingSeconds").asInt()).isEqualTo(3600);

        // Luot 1 van xem lai duoc va giu trang thai da ket thuc
        mockMvc.perform(auth(get(SESSIONS + "/attempts/" + first.get("attemptId").asText()), student(3)))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.attemptNo").value(1));

        // Luot 2 het gio (khong bam ket thuc): da dung het 2/2 luot
        clock.advance(Duration.ofMinutes(61));
        expectError(join(student(3), code, passcode), 409, "EXAM_ATTEMPT_LIMIT_REACHED");

        // Sinh vien khac khong bi anh huong, va giang vien thay tong 3 luot thi
        join(student(4), code, passcode)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptNo").value(1));
        mockMvc.perform(auth(get(SESSIONS + "/" + code), LECTURER_1))
                .andExpect(jsonPath("$.data.attemptCount").value(3));

        // Phien da mo: tang so luot thi duoc, giam thi khong
        body.put("maxAttempts", 1);
        expectError(mockMvc.perform(json(auth(put(SESSIONS + "/" + code), LECTURER_1), body)),
                409, "EXAM_MAX_ATTEMPTS_CANNOT_REDUCE");
        body.put("maxAttempts", 3);
        mockMvc.perform(json(auth(put(SESSIONS + "/" + code), LECTURER_1), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAttempts").value(3));
        // Sua ma khong gui maxAttempts thi giu nguyen
        body.remove("maxAttempts");
        mockMvc.perform(json(auth(put(SESSIONS + "/" + code), LECTURER_1), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAttempts").value(3));

        // Co them luot nen sinh vien 3 vao thi tiep duoc: luot 3
        join(student(3), code, passcode)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptNo").value(3))
                .andExpect(jsonPath("$.data.maxAttempts").value(3));

        // Het gio mo phien thi du con luot cung khong vao duoc
        clock.set(end.plusSeconds(1));
        expectError(join(student(4), code, passcode), 409, "EXAM_SESSION_ENDED");
    }

    @Test
    void users_haveNoStudentCode() throws Exception {
        mockMvc.perform(auth(get("/api/auth/me"), student(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", matchesPattern("^ST\\d{6}$")))
                .andExpect(jsonPath("$.data.studentCode").doesNotExist());
    }

    /* ========================= XOA / HUY ========================= */

    @Test
    void delete_rules() throws Exception {
        Instant start = clock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        // Chua co sinh vien: xoa han
        String emptyUrl = SESSIONS + "/" + createAndGet(LECTURER_1, body("Phiên trống", start, end, 60)).get("id").asText();
        mockMvc.perform(auth(delete(emptyUrl), LECTURER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("DELETED"));
        expectError(mockMvc.perform(auth(get(emptyUrl), LECTURER_1)), 404, "EXAM_SESSION_NOT_FOUND");
        expectError(mockMvc.perform(auth(delete(emptyUrl), LECTURER_1)), 404, "EXAM_SESSION_NOT_FOUND");

        // Da co sinh vien va dang mo: chi huy
        JsonNode session = createAndGet(LECTURER_1, body("Phiên có SV", start, end, 60));
        String url = SESSIONS + "/" + session.get("id").asText();
        String code = session.get("id").asText();
        String passcode = session.get("passcode").asText();
        clock.set(start);
        String attemptId = data(join(student(6), code, passcode)).get("attemptId").asText();

        mockMvc.perform(auth(delete(url), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("CANCELLED"));
        mockMvc.perform(auth(get(url), LECTURER_1))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.attemptCount").value(1));
        // Huy lan nua: van CANCELLED
        mockMvc.perform(auth(delete(url), LECTURER_1)).andExpect(jsonPath("$.data.action").value("CANCELLED"));

        // Phien da huy: khong sua, khong tao lai ma, khong vao thi, khong xem luot thi
        expectError(mockMvc.perform(json(auth(put(url), LECTURER_1), body("Phiên", start, end, 60))), 409, "EXAM_SESSION_CANCELLED");
        expectError(mockMvc.perform(auth(post(url + "/regenerate-passcode"), LECTURER_1)), 409, "EXAM_SESSION_CANCELLED");
        expectError(join(student(6), code, passcode), 409, "EXAM_SESSION_CANCELLED");
        expectError(join(student(7), code, passcode), 409, "EXAM_SESSION_CANCELLED");
        expectError(mockMvc.perform(auth(get(SESSIONS + "/attempts/" + attemptId), student(6))), 409, "EXAM_SESSION_CANCELLED");

        // Da ket thuc va co sinh vien: khong xoa duoc
        Instant start2 = clock.instant().plus(1, ChronoUnit.HOURS);
        JsonNode ended = createAndGet(LECTURER_1, body("Phiên sẽ kết thúc", start2, start2.plus(1, ChronoUnit.HOURS), 30));
        clock.set(start2);
        join(student(6), ended.get("id").asText(), ended.get("passcode").asText()).andExpect(status().isOk());
        clock.set(start2.plus(2, ChronoUnit.HOURS));
        expectError(mockMvc.perform(auth(delete(SESSIONS + "/" + ended.get("id").asText()), LECTURER_1)),
                409, "EXAM_SESSION_HAS_ATTEMPTS");
    }

    /* ========================= DANH SACH ========================= */

    @Test
    void list_filtersAndPaging() throws Exception {
        Instant now = clock.instant();
        String tag = "LOC" + now.getEpochSecond();
        JsonNode upcoming = createAndGet(LECTURER_2, body(tag + " sắp mở", now.plus(5, ChronoUnit.HOURS), now.plus(6, ChronoUnit.HOURS), 30));
        JsonNode ongoing = createAndGet(LECTURER_2, body(tag + " đang mở", now, now.plus(8, ChronoUnit.HOURS), 30));
        JsonNode ended = createAndGet(LECTURER_2, body(tag + " đã xong", now, now.plus(30, ChronoUnit.MINUTES), 30));
        clock.advance(Duration.ofHours(1));

        mockMvc.perform(auth(get(SESSIONS).param("keyword", tag), LECTURER_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(3))
                // Sap xep theo gio mo giam dan
                .andExpect(jsonPath("$.data.items[0].id").value(upcoming.get("id").asText()));
        mockMvc.perform(auth(get(SESSIONS).param("keyword", tag).param("status", "ongoing"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(ongoing.get("id").asText()));
        mockMvc.perform(auth(get(SESSIONS).param("keyword", tag).param("status", "ENDED"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(ended.get("id").asText()));
        mockMvc.perform(auth(get(SESSIONS).param("keyword", tag).param("status", "UPCOMING"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mockMvc.perform(auth(get(SESSIONS).param("keyword", tag).param("status", "CANCELLED"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // Tim theo ma phien
        mockMvc.perform(auth(get(SESSIONS).param("keyword", ongoing.get("id").asText().toLowerCase()), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        // Ky tu % va _ duoc coi la chu thuong, khong phai ky tu dai dien
        mockMvc.perform(auth(get(SESSIONS).param("keyword", "%"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // Phan trang
        mockMvc.perform(auth(get(SESSIONS).param("keyword", tag).param("size", "2").param("page", "1"), LECTURER_2))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.page").value(1));

        // Tham so sai
        expectError(mockMvc.perform(auth(get(SESSIONS).param("status", "DONE"), LECTURER_2)), 400, "BAD_REQUEST");
        expectError(mockMvc.perform(auth(get(SESSIONS).param("page", "abc"), LECTURER_2)), 400, "BAD_REQUEST");
    }

    /* ========================= HO TRO ========================= */

    private String student(int number) {
        return "student0" + number + "@fpt.edu.vn";
    }

    private String passwordOf(String email) {
        return ADMIN.equals(email) ? "Admin@123" : "123456";
    }

    private String token(String email) throws Exception {
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

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String email) throws Exception {
        return builder.header("Authorization", "Bearer " + token(email));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private Map<String, Object> body(String title, Instant startAt, Instant endAt, int durationMinutes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "Mô tả");
        body.put("startAt", startAt.toString());
        body.put("endAt", endAt.toString());
        body.put("durationMinutes", durationMinutes);
        return body;
    }

    private ResultActions create(String email, Map<String, Object> body) throws Exception {
        return mockMvc.perform(json(auth(post(SESSIONS), email), body));
    }

    private JsonNode createAndGet(String email, Map<String, Object> body) throws Exception {
        return data(create(email, body).andExpect(status().isCreated()));
    }

    private ResultActions join(String email, String examCode, String passcode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", examCode);
        body.put("passcode", passcode);
        return mockMvc.perform(json(auth(post(SESSIONS + "/join"), email), body));
    }

    private JsonNode data(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(content).get("data");
    }

    private void expectError(ResultActions actions, int httpStatus, String code) throws Exception {
        actions.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** Ma truy cap chac chan sai: doi chu so cuoi. */
    private String wrong(String passcode) {
        char last = passcode.charAt(5);
        return passcode.substring(0, 5) + (last == '9' ? '0' : (char) (last + 1));
    }
}
