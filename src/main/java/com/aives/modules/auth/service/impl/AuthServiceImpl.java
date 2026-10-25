package com.aives.modules.auth.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.JwtTokenProvider;
import com.aives.common.security.TokenBlacklist;
import com.aives.common.security.UserPrincipal;
import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.dto.GoogleLoginRequest;
import com.aives.modules.auth.dto.ForgotPasswordRequest;
import com.aives.modules.auth.dto.LoginRequest;
import com.aives.modules.auth.dto.LoginResponse;
import com.aives.modules.auth.dto.RefreshTokenRequest;
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.ResetPasswordRequest;
import com.aives.modules.auth.dto.TokenRefreshResponse;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.entity.Role;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.OtpType;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.mapper.UserMapper;
import com.aives.modules.auth.repository.RoleRepository;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.service.AuthService;
import com.aives.modules.auth.service.OtpService;
import com.aives.modules.auth.service.UserService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklist tokenBlacklist;
    private final UserMapper userMapper;

    // UserId -> Set of active refresh token JTIs
    private final Map<String, Set<String>> userActiveRefreshTokens = new ConcurrentHashMap<>();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Tu dang ky thi luon la STUDENT, khong cho client chon role.
        return userService.registerStudent(request);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(UserPrincipal.from(user));
        String refreshToken = jwtTokenProvider.generateRefreshToken(UserPrincipal.from(user));
        recordActiveRefreshToken(user.getId(), refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .refreshExpiresIn(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken idToken;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            idToken = verifier.verify(request.getIdToken());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Xác thực tài khoản Google không thành công");
        }

        if (idToken == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Xác thực tài khoản Google không thành công");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Xác thực tài khoản Google không thành công");
        }
        email = email.trim().toLowerCase(Locale.ROOT);

        Optional<User> optionalUser = userRepository.findByEmail(email);
        User user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            if (!user.isActive()) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
        } else {
            Role studentRole = roleRepository.findByName(RoleName.STUDENT)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Thiếu role STUDENT trong bảng roles"));

            String userId;
            do {
                userId = RandomIdGenerator.generate("ST", 6);
            } while (userRepository.existsById(userId));

            String name = (String) payload.get("name");
            if (!StringUtils.hasText(name)) {
                name = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            }

            user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .fullName(name.trim())
                    .role(studentRole)
                    .active(true)
                    .mustChangePassword(false)
                    .build();
            user.setId(userId);
            user = userRepository.save(user);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(UserPrincipal.from(user));
        String refreshToken = jwtTokenProvider.generateRefreshToken(UserPrincipal.from(user));
        recordActiveRefreshToken(user.getId(), refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .refreshExpiresIn(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token không được để trống");
        }

        String rawToken = request.getRefreshToken().trim();
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(rawToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token không hợp lệ hoặc đã hết hạn");
        }

        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Token cung cấp không phải là refresh token hợp lệ");
        }

        String jti = claims.getId();
        if (jti != null && tokenBlacklist.isBlacklisted(jti)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token đã bị vô hiệu hoá hoặc đã được sử dụng");
        }

        String userId = claims.get("uid", String.class);
        User user = null;
        if (StringUtils.hasText(userId)) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && StringUtils.hasText(claims.getSubject())) {
            user = userRepository.findByEmail(claims.getSubject().trim().toLowerCase(Locale.ROOT)).orElse(null);
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Tài khoản người dùng không tồn tại");
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // Invalidate submitted refresh token to enforce single-use rotation
        if (jti != null) {
            Date exp = claims.getExpiration();
            Instant expInstant = exp != null ? exp.toInstant() : Instant.now().plus(7, ChronoUnit.DAYS);
            tokenBlacklist.blacklist(jti, expInstant);
            removeActiveRefreshToken(user.getId(), jti);
        }

        UserPrincipal principal = UserPrincipal.from(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(principal);
        recordActiveRefreshToken(user.getId(), newRefreshToken);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .refreshExpiresIn(jwtTokenProvider.getRefreshTokenExpirationSeconds())
                .build();
    }

    @Override
    public void logout(String authorizationHeader) {
        logout(authorizationHeader, null);
    }

    @Override
    public void logout(String authorizationHeader, RefreshTokenRequest request) {
        if (StringUtils.hasText(authorizationHeader)) {
            String token = jwtTokenProvider.resolveToken(authorizationHeader);
            if (token != null) {
                try {
                    Claims claims = jwtTokenProvider.parseClaims(token);
                    tokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
                    String userId = claims.get("uid", String.class);
                    if (StringUtils.hasText(userId)) {
                        revokeAllRefreshTokensForUser(userId);
                    }
                } catch (JwtException | IllegalArgumentException ignored) {
                }
            }
        }

        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            try {
                Claims refreshClaims = jwtTokenProvider.parseClaims(request.getRefreshToken().trim());
                if (refreshClaims.getId() != null) {
                    Date exp = refreshClaims.getExpiration();
                    Instant expInstant = exp != null ? exp.toInstant() : Instant.now().plus(7, ChronoUnit.DAYS);
                    tokenBlacklist.blacklist(refreshClaims.getId(), expInstant);
                    String userId = refreshClaims.get("uid", String.class);
                    if (StringUtils.hasText(userId)) {
                        removeActiveRefreshToken(userId, refreshClaims.getId());
                    }
                }
            } catch (JwtException | IllegalArgumentException ignored) {
            }
        }
    }

    private void recordActiveRefreshToken(String userId, String refreshToken) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(refreshToken)) {
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseClaims(refreshToken);
            if (claims.getId() != null) {
                userActiveRefreshTokens.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(claims.getId());
            }
        } catch (Exception ignored) {
        }
    }

    private void removeActiveRefreshToken(String userId, String jti) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(jti)) {
            return;
        }
        Set<String> set = userActiveRefreshTokens.get(userId);
        if (set != null) {
            set.remove(jti);
        }
    }

    private void revokeAllRefreshTokensForUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        Set<String> tokenIds = userActiveRefreshTokens.remove(userId);
        if (tokenIds != null) {
            Instant defaultExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
            for (String jti : tokenIds) {
                tokenBlacklist.blacklist(jti, defaultExpiry);
            }
        }
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        otpService.sendPasswordResetOtp(request.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        otpService.verifyAndConsumeOtp(email, request.getOtpCode(), OtpType.PASSWORD_RESET);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}

