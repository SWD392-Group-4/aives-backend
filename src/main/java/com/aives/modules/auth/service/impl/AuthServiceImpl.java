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
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.ResetPasswordRequest;
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
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
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

        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = jwtTokenProvider.resolveToken(authorizationHeader);
        if (token == null) {
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            // Dua token vao blacklist cho den khi no tu het han
            tokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            // Token da sai / het han san thi khong can lam gi
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

