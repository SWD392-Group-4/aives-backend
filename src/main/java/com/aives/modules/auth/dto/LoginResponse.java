package com.aives.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    // Thoi gian song cua access token, tinh bang giay
    private long expiresIn;
    // Thoi gian song cua refresh token, tinh bang giay
    private long refreshExpiresIn;
    private UserResponse user;
}
