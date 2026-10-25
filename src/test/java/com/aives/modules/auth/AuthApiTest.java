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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
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
class AuthApiTest extends ApiTestSupport {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("OpenAPI Spec: POST /api/v1/auth/login trả về đầy đủ access & refresh tokens với metadata")
    void testLoginOpenApiSpec() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", ADMIN);
        body.put("password", passwordOf(ADMIN));

        mockMvc.perform(json(post("/api/v1/auth/login"), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", equalTo("Bearer")))
                .andExpect(jsonPath("$.data.expiresIn", equalTo(3600)))
                .andExpect(jsonPath("$.data.refreshExpiresIn", equalTo(604800)))
                .andExpect(jsonPath("$.data.user.email", equalTo(ADMIN)));
    }

    @Test
    @DisplayName("OpenAPI Spec: POST /api/v1/auth/refresh làm mới token thành công và xoay vòng refresh token")
    void testRefreshTokenSuccess() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", student(1));
        loginBody.put("password", passwordOf(student(1)));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String originalRefreshToken = loginData.get("refreshToken").asText();

        Map<String, Object> refreshBody = Map.of("refreshToken", originalRefreshToken);
        JsonNode refreshData = data(mockMvc.perform(json(post("/api/v1/auth/refresh"), refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", equalTo("Bearer"))));

        String newAccessToken = refreshData.get("accessToken").asText();

        // New access token can access protected endpoints
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email", equalTo(student(1))));
    }

    @Test
    @DisplayName("Negative Case: Refresh token hết hạn bị từ chối với mã lỗi 401 AUTH_INVALID_TOKEN")
    void testExpiredRefreshTokenRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(ADMIN)
                .claim("uid", "admin-user-id")
                .claim("role", "ADMIN")
                .claim("typ", "refresh")
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.DAYS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))
                .signWith(key)
                .compact();

        Map<String, Object> body = Map.of("refreshToken", expiredToken);
        mockMvc.perform(json(post("/api/v1/auth/refresh"), body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }

    @Test
    @DisplayName("Negative Case: Refresh token có chữ ký không hợp lệ bị từ chối với 401 AUTH_INVALID_TOKEN")
    void testInvalidSignatureRefreshTokenRejected() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor("a-completely-different-secret-key-that-is-at-least-256-bits-long-for-test".getBytes(StandardCharsets.UTF_8));
        String tamperedSignatureToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(ADMIN)
                .claim("uid", "admin-user-id")
                .claim("role", "ADMIN")
                .claim("typ", "refresh")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
                .signWith(wrongKey)
                .compact();

        Map<String, Object> body = Map.of("refreshToken", tamperedSignatureToken);
        mockMvc.perform(json(post("/api/v1/auth/refresh"), body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }

    @Test
    @DisplayName("Negative Case: Refresh token và access token bị thu hồi sau khi logout")
    void testRevokedTokenAfterLogoutRejected() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", LECTURER_1);
        loginBody.put("password", passwordOf(LECTURER_1));

        JsonNode loginData = data(mockMvc.perform(json(post("/api/v1/auth/login"), loginBody))
                .andExpect(status().isOk()));
        String accessToken = loginData.get("accessToken").asText();
        String refreshToken = loginData.get("refreshToken").asText();

        // Logout
        Map<String, Object> logoutBody = Map.of("refreshToken", refreshToken);
        mockMvc.perform(json(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken), logoutBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));

        // Revoked access token cannot access /me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // Revoked refresh token cannot be refreshed
        mockMvc.perform(json(post("/api/v1/auth/refresh"), logoutBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("AUTH_INVALID_TOKEN")));
    }
}
