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
@Schema(description = "Yêu cầu làm mới access token")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token không được để trống")
    @Schema(description = "Refresh token đã được cấp trước đó", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
}
