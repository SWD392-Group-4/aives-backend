package com.aives.modules.auth;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aives.testsupport.AiTestBeans;
import com.aives.testsupport.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiTestBeans.class)
@Transactional
class AuthLifecycleAndRefreshTokenTest extends ApiTestSupport {

    @Test
    @DisplayName("Dual Route Login: Hỗ trợ cả /api/auth/login và /api/v1/auth/login trả về token pair")
    void testDualRouteLoginSuccess() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", ADMIN);
        body.put("password", passwordOf(ADMIN));

        // 1. Kiểm tra route legacy /api/auth/login
        mockMvc.perform(json(post("/api/auth/login"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", equalTo("Bearer")))
                .andExpect(jsonPath("$.data.expiresIn", equalTo(3600)))
                .andExpect(jsonPath("$.data.refreshExpiresIn", equalTo(604800)))
                .andExpect(jsonPath("$.data.user.email", equalTo(ADMIN)));

        // 2. Kiểm tra route chuẩn OpenAPI /api/v1/auth/login
        mockMvc.perform(json(post("/api/v1/auth/login"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", equalTo("Bearer")))
                .andExpect(jsonPath("$.data.user.email", equalTo(ADMIN)));
    }

    @Test
    @DisplayName("Dual Route Refresh: Làm mới token thành công qua cả /api/auth/refresh và /api/v1/auth/refresh")
    void testDualRouteRefreshTokenSuccess() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", LECTURER_1);
        loginBody.put("password", passwordOf(LECTURER_1));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String refreshToken1 = loginData.get("refreshToken").asText();

        // 1. Refresh qua route /api/auth/refresh
        Map<String, Object> refreshBody1 = Map.of("refreshToken", refreshToken1);
        JsonNode refreshData1 = data(mockMvc.perform(json(post("/api/auth/refresh"), refreshBody1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", equalTo("Bearer"))));

        String refreshToken2 = refreshData1.get("refreshToken").asText();

        // 2. Refresh qua route /api/v1/auth/refresh
        Map<String, Object> refreshBody2 = Map.of("refreshToken", refreshToken2);
        data(mockMvc.perform(json(post("/api/v1/auth/refresh"), refreshBody2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue())));
    }

    @Test
    @DisplayName("Rotation Invariant: Tái sử dụng refresh token cũ đã xoay vòng phải bị từ chối với AUTH_INVALID_TOKEN")
    void testRefreshTokenRotationEnforcement() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", student(1));
        loginBody.put("password", passwordOf(student(1)));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String originalRefreshToken = loginData.get("refreshToken").asText();

        // Bước 1: Refresh lần đầu thành công
        Map<String, Object> refreshBody = Map.of("refreshToken", originalRefreshToken);
        mockMvc.perform(json(post("/api/v1/auth/refresh"), refreshBody))
                .andExpect(status().isOk());

        // Bước 2: Dùng lại originalRefreshToken (đã bị xoay vòng) -> 401 AUTH_INVALID_TOKEN
        mockMvc.perform(json(post("/api/v1/auth/refresh"), refreshBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }

    @Test
    @DisplayName("Security: Gửi token sai định dạng hoặc tampered phải bị từ chối với AUTH_INVALID_TOKEN")
    void testInvalidAndTamperedRefreshToken() throws Exception {
        Map<String, Object> tamperedBody = Map.of("refreshToken", "invalid.jwt.token.string");

        mockMvc.perform(json(post("/api/v1/auth/refresh"), tamperedBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }

    @Test
    @DisplayName("Security: Không được dùng Access Token để gọi endpoint /refresh")
    void testAccessTokenCannotBeUsedAsRefreshToken() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", student(2));
        loginBody.put("password", passwordOf(student(2)));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String accessToken = loginData.get("accessToken").asText();

        Map<String, Object> body = Map.of("refreshToken", accessToken);
        mockMvc.perform(json(post("/api/v1/auth/refresh"), body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }

    @Test
    @DisplayName("Security: Không được dùng Refresh Token làm Bearer token để gọi API bảo mật")
    void testRefreshTokenCannotAccessProtectedEndpoints() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", student(3));
        loginBody.put("password", passwordOf(student(3)));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String refreshToken = loginData.get("refreshToken").asText();

        // Thử dùng refresh token trong header Authorization
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout: Đăng xuất vô hiệu hoá cả access token và refresh token")
    void testLogoutInvalidatesTokens() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", LECTURER_2);
        loginBody.put("password", passwordOf(LECTURER_2));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String accessToken = loginData.get("accessToken").asText();
        String refreshToken = loginData.get("refreshToken").asText();

        // 1. Thao tác đăng xuất qua POST /api/v1/auth/logout kèm refresh token
        Map<String, Object> logoutBody = Map.of("refreshToken", refreshToken);
        mockMvc.perform(json(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken), logoutBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));

        // 2. Access token cũ không còn dùng được nữa
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // 3. Refresh token cũng không còn làm mới được nữa
        Map<String, Object> refreshBody = Map.of("refreshToken", refreshToken);
        mockMvc.perform(json(post("/api/v1/auth/refresh"), refreshBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }
}
