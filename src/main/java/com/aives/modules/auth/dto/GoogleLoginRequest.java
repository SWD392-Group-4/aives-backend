package com.aives.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Yêu cầu đăng nhập bằng Google ID Token")
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token không được để trống")
    @Schema(description = "Google ID Token (JWT do Google OAuth cung cấp)", example = "eyJhbGciOiJSUzI1NiIs...")
    private String idToken;
}
