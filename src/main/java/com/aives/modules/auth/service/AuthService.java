package com.aives.modules.auth.service;

import com.aives.modules.auth.dto.ForgotPasswordRequest;
import com.aives.modules.auth.dto.LoginRequest;
import com.aives.modules.auth.dto.LoginResponse;
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.ResetPasswordRequest;
import com.aives.modules.auth.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String authorizationHeader);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}

