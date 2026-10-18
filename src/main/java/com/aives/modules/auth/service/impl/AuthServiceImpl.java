package com.aives.modules.auth.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.security.JwtTokenProvider;
import com.aives.common.security.TokenBlacklist;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.dto.ForgotPasswordRequest;
import com.aives.modules.auth.dto.LoginRequest;
import com.aives.modules.auth.dto.LoginResponse;
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.ResetPasswordRequest;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.OtpType;
import com.aives.modules.auth.mapper.UserMapper;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.service.AuthService;
import com.aives.modules.auth.service.OtpService;
import com.aives.modules.auth.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklist tokenBlacklist;
    private final UserMapper userMapper;

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

