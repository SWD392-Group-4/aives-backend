package com.aives.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.integration.mail.MailMessage;
import com.aives.integration.mail.MailService;
import com.aives.modules.auth.entity.Role;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.repository.RoleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Test tai khoan tren H2 (profile dev): bang roles tach rieng, admin tao tai khoan -> gui mail mat khau tam
 * -> bat doi mat khau o lan dang nhap dau. Email duoc bat lai bang CapturingMailService, khong gui that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminUserApiTest {

    private static final String ADMIN = "admin@aives.local";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final Pattern TEMP_PASSWORD_LINE = Pattern.compile("Mật khẩu tạm: (\\S+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CapturingMailService mailService;

    @Autowired
    private RoleRepository roleRepository;

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        @Primary
        CapturingMailService testMailService() {
            return new CapturingMailService();
        }
    }

    /** Thay cho SMTP: giu lai email da "gui", va co the gia lap gui that bai. */
    static class CapturingMailService implements MailService {
        final List<MailMessage> sent = new ArrayList<>();
        boolean fail;

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void send(MailMessage message) {
            if (fail) {
                throw new MailSendException("SMTP không phản hồi");
            }
            sent.add(message);
        }
    }

    @BeforeEach
    void resetMail() {
        mailService.sent.clear();
        mailService.fail = false;
    }

    @Test
    void roles_areStoredInTheirOwnTable() throws Exception {
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).hasSize(3);
        for (RoleName name : RoleName.values()) {
            Role role = roleRepository.findByName(name).orElseThrow();
            // roles_id cung la tien to cua users_id
            assertThat(role.getId()).isEqualTo(name.getIdPrefix());
        }

        // API van tra role dang chuoi nhu truoc, FE khong phai doi
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.id", matchesPattern("^AD\\d{6}$")))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void adminCreate_sendsTemporaryPasswordAndForcesPasswordChange() throws Exception {
        String adminToken = login(ADMIN, ADMIN_PASSWORD);
        String email = "new.lecturer@fe.edu.vn";

        // Admin khong nhap mat khau; mat khau khong bao gio co trong response
        ResultActions created = createUser(adminToken, "New.Lecturer@fe.edu.vn", "Giảng viên <b>Mới</b>", "LECTURER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailSent").value(true))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.role").value("LECTURER"))
                .andExpect(jsonPath("$.data.user.id", matchesPattern("^LE\\d{6}$")))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
        String responseBody = created.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Dung 1 email toi dung nguoi nhan, co mat khau tam va ho ten da duoc escape trong ban HTML
        assertThat(mailService.sent).hasSize(1);
        MailMessage mail = mailService.sent.get(0);
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.subject()).contains("AIVES");
        Matcher matcher = TEMP_PASSWORD_LINE.matcher(mail.textBody());
        assertThat(matcher.find()).isTrue();
        String tempPassword = matcher.group(1);
        assertThat(tempPassword).hasSize(10).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*\\d.*");
        assertThat(mail.htmlBody()).contains(tempPassword).contains("&lt;b&gt;").doesNotContain("<b>Mới</b>");
        assertThat(mail.htmlBody()).contains("/login");
        assertThat(responseBody).doesNotContain(tempPassword);

        // Dang nhap bang mat khau tam duoc, nhung bi danh dau phai doi mat khau
        JsonNode loginData = loginData(email, tempPassword);
        assertThat(loginData.get("user").get("mustChangePassword").asBoolean()).isTrue();
        String token = loginData.get("accessToken").asText();

        // Chua doi mat khau: chi goi duoc /api/auth/**
        expectError(mockMvc.perform(get("/api/exam-sessions").header("Authorization", "Bearer " + token)),
                403, "PASSWORD_CHANGE_REQUIRED");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        // Mat khau moi khong duoc trung mat khau tam
        expectError(changePassword(token, tempPassword, tempPassword), 400, "NEW_PASSWORD_SAME_AS_CURRENT");
        expectError(changePassword(token, "sai-mat-khau", "MatKhauMoi1"), 400, "WRONG_CURRENT_PASSWORD");
        changePassword(token, tempPassword, "MatKhauMoi1").andExpect(status().isOk());

        // Doi xong: dung binh thuong, mat khau tam het hieu luc
        mockMvc.perform(get("/api/exam-sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode afterChange = loginData(email, "MatKhauMoi1");
        assertThat(afterChange.get("user").get("mustChangePassword").asBoolean()).isFalse();
        expectError(mockMvc.perform(json(post("/api/auth/login"), credentials(email, tempPassword))),
                401, "INVALID_CREDENTIALS");
    }

    @Test
    void adminCreate_rollsBackWhenMailCannotBeSent() throws Exception {
        String adminToken = login(ADMIN, ADMIN_PASSWORD);
        String email = "mail.fail@fpt.edu.vn";

        mailService.fail = true;
        expectError(createUser(adminToken, email, "Gửi Mail Lỗi", "STUDENT"), 502, "MAIL_SEND_FAILED");

        // Tai khoan khong duoc tao: danh sach khong co email nay
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.email == '" + email + "')]", hasSize(0)));

        // Gui lai khi mail hoat dong: tao duoc voi dung email do (khong bi bao trung)
        mailService.fail = false;
        createUser(adminToken, email, "Gửi Mail Lỗi", "STUDENT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id", matchesPattern("^ST\\d{6}$")));
        assertThat(mailService.sent).hasSize(1);
    }

    @Test
    void adminCreate_validatesInputAndPermissions() throws Exception {
        String adminToken = login(ADMIN, ADMIN_PASSWORD);

        // Thieu email / role sai
        expectError(createUser(adminToken, "", "Tên", "STUDENT"), 400, "VALIDATION_ERROR");
        expectError(createUser(adminToken, "khong-phai-email", "Tên", "STUDENT"), 400, "VALIDATION_ERROR");
        // Email da ton tai
        expectError(createUser(adminToken, "student01@fpt.edu.vn", "Tên", "STUDENT"), 409, "EMAIL_ALREADY_EXISTS");
        // Khong phai admin
        String studentToken = login("student01@fpt.edu.vn", "123456");
        expectError(createUser(studentToken, "x@fpt.edu.vn", "Tên", "STUDENT"), 403, "ACCESS_DENIED");

        assertThat(mailService.sent).isEmpty();
    }

    @Test
    void register_keepsOwnPasswordAndSendsNoMail() throws Exception {
        Map<String, Object> sendOtpBody = new LinkedHashMap<>();
        sendOtpBody.put("email", "tu.dang.ky@fpt.edu.vn");
        mockMvc.perform(json(post("/api/auth/send-otp"), sendOtpBody))
                .andExpect(status().isOk());
        assertThat(mailService.sent).hasSize(1);
        MailMessage otpMail = mailService.sent.remove(0);
        Matcher matcher = Pattern.compile("Mã xác thực \\(OTP\\) của bạn là: (\\d{6})").matcher(otpMail.textBody());
        assertThat(matcher.find()).isTrue();
        String otpCode = matcher.group(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "tu.dang.ky@fpt.edu.vn");
        body.put("password", "MatKhau123");
        body.put("fullName", "Tự Đăng Ký");
        body.put("otp", otpCode);
        mockMvc.perform(json(post("/api/auth/register"), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.id", matchesPattern("^ST\\d{6}$")))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        assertThat(mailService.sent).isEmpty();
        String token = login("tu.dang.ky@fpt.edu.vn", "MatKhau123");
        // Sinh vien tu dang ky dung he thong ngay, khong bi bat doi mat khau
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("examId", "AIVES_EXAM_2020_000000");
        join.put("passcode", "000000");
        expectError(mockMvc.perform(json(post("/api/exam-sessions/join"), join).header("Authorization", "Bearer " + token)),
                400, "EXAM_JOIN_INVALID");
    }

    @Test
    void adminGetUsers_paginationAndFilters() throws Exception {
        String adminToken = login(ADMIN, ADMIN_PASSWORD);

        // Loc theo role ADMIN
        mockMvc.perform(get("/api/admin/users?role=ADMIN&page=0&size=10").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        // Loc theo keyword
        mockMvc.perform(get("/api/admin/users?keyword=admin").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].email").value(ADMIN));
    }

    @Test
    void forgotPasswordAndResetPasswordFlow() throws Exception {
        // 1. Email khong ton tai -> USER_NOT_FOUND
        Map<String, Object> notFoundBody = new LinkedHashMap<>();
        notFoundBody.put("email", "unknown_user@fpt.edu.vn");
        expectError(mockMvc.perform(json(post("/api/auth/forgot-password"), notFoundBody)),
                404, "USER_NOT_FOUND");

        // 2. Yeu cau OTP quen mat khau cho admin
        Map<String, Object> forgotBody = new LinkedHashMap<>();
        forgotBody.put("email", ADMIN);
        mockMvc.perform(json(post("/api/auth/forgot-password"), forgotBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(mailService.sent).hasSize(1);
        MailMessage mail = mailService.sent.get(0);
        Matcher matcher = Pattern.compile("Mã xác thực \\(OTP\\) của bạn là: (\\d{6})").matcher(mail.textBody());
        assertThat(matcher.find()).isTrue();
        String otpCode = matcher.group(1);

        // 3. Reset password voi OTP sai -> OTP_INVALID
        Map<String, Object> wrongOtpBody = new LinkedHashMap<>();
        wrongOtpBody.put("email", ADMIN);
        wrongOtpBody.put("otpCode", "000000");
        wrongOtpBody.put("newPassword", "NewAdmin@123");
        expectError(mockMvc.perform(json(post("/api/auth/reset-password"), wrongOtpBody)),
                400, "OTP_INVALID");

        // 4. Reset password thanh cong voi OTP dung
        Map<String, Object> resetBody = new LinkedHashMap<>();
        resetBody.put("email", ADMIN);
        resetBody.put("otpCode", otpCode);
        resetBody.put("newPassword", "NewAdmin@123");
        mockMvc.perform(json(post("/api/auth/reset-password"), resetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Dang nhap bang mat khau cu that bai
        expectError(mockMvc.perform(json(post("/api/auth/login"), credentials(ADMIN, ADMIN_PASSWORD))),
                401, "INVALID_CREDENTIALS");

        // 6. Dang nhap bang mat khau moi thanh cong
        String newToken = login(ADMIN, "NewAdmin@123");
        assertThat(newToken).isNotBlank();

        // Khoi phuc lai mat khau cu de cac test khac khong bi anh huong
        changePassword(newToken, "NewAdmin@123", ADMIN_PASSWORD).andExpect(status().isOk());
    }

    /* ========================= HO TRO ========================= */

    private ResultActions createUser(String adminToken, String email, String fullName, String role) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("fullName", fullName);
        body.put("role", role);
        return mockMvc.perform(json(post("/api/admin/users"), body).header("Authorization", "Bearer " + adminToken));
    }

    private ResultActions changePassword(String token, String currentPassword, String newPassword) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        return mockMvc.perform(json(put("/api/auth/me/password"), body).header("Authorization", "Bearer " + token));
    }

    private Map<String, Object> credentials(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private JsonNode loginData(String email, String password) throws Exception {
        String content = mockMvc.perform(json(post("/api/auth/login"), credentials(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content).get("data");
    }

    private String login(String email, String password) throws Exception {
        return loginData(email, password).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private void expectError(ResultActions actions, int httpStatus, String code) throws Exception {
        actions.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code));
    }
}
