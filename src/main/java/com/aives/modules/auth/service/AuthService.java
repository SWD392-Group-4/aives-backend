package com.aives.modules.auth.service;

import com.aives.modules.auth.dto.LoginRequest;
import com.aives.modules.auth.dto.LoginResponse;
import com.aives.modules.auth.dto.UserResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout(String authorizationHeader);

    UserResponse getCurrentUser(Long userId);
}
