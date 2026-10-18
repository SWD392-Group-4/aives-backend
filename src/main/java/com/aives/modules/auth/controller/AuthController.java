package com.aives.modules.auth.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.auth.dto.ChangePasswordRequest;
import com.aives.modules.auth.dto.ForgotPasswordRequest;
import com.aives.modules.auth.dto.GoogleLoginRequest;
import com.aives.modules.auth.dto.LoginRequest;
import com.aives.modules.auth.dto.LoginResponse;
import com.aives.modules.auth.dto.RegisterRequest;
import com.aives.modules.auth.dto.ResetPasswordRequest;
import com.aives.modules.auth.dto.SendOtpRequest;
import com.aives.modules.auth.dto.UpdateProfileRequest;
import com.aives.modules.auth.dto.UserResponse;
import com.aives.modules.auth.service.AuthService;
import com.aives.modules.auth.service.OtpService;
import com.aives.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Đăng ký / đăng nhập / đăng xuất / profile (mọi role)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final OtpService otpService;

    @Operation(summary = "Gửi mã OTP xác thực email (cho luồng đăng ký sinh viên)")
    @SecurityRequirements // API nay khong can token
    @PostMapping("/send-otp")
    public ApiResponse<Void> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.sendRegistrationOtp(request.getEmail());
        return ApiResponse.ok("Mã xác thực OTP đã được gửi đến email của bạn", null);
    }

    @Operation(summary = "Yêu cầu gửi mã OTP đặt lại mật khẩu")
    @SecurityRequirements // API nay khong can token
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok("Mã xác thực OTP đặt lại mật khẩu đã được gửi đến email của bạn", null);
    }

    @Operation(summary = "Đặt lại mật khẩu với mã xác thực OTP")
    @SecurityRequirements // API nay khong can token
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập với mật khẩu mới", null);
    }

    @Operation(summary = "Sinh viên tự đăng ký tài khoản (role luôn là STUDENT)")
    @SecurityRequirements // API nay khong can token
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Đăng ký thành công", authService.register(request));
    }

    @Operation(summary = "Đăng nhập, trả về accessToken (JWT)")
    @SecurityRequirements // API nay khong can token
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Đăng nhập thành công", authService.login(request));
    }

    @Operation(summary = "Đăng nhập bằng tài khoản Google")
    @SecurityRequirements // API nay khong can token
    @PostMapping("/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok("Đăng nhập bằng Google thành công", authService.loginWithGoogle(request));
    }

    @Operation(summary = "Đăng xuất, vô hiệu hoá token hiện tại")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ApiResponse.ok("Đăng xuất thành công", null);
    }

    @Operation(summary = "Xem profile của tài khoản đang đăng nhập")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.getUserById(principal.getId()));
    }

    @Operation(summary = "Cập nhật profile của tài khoản đang đăng nhập")
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("Cập nhật profile thành công", userService.updateProfile(principal.getId(), request));
    }

    @Operation(summary = "Đổi mật khẩu của tài khoản đang đăng nhập")
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ApiResponse.ok("Đổi mật khẩu thành công", null);
    }
}
