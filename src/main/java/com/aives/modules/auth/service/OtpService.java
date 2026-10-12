package com.aives.modules.auth.service;

import com.aives.modules.auth.enums.OtpType;

public interface OtpService {

    /**
     * Sinh và gửi mã OTP xác thực đăng ký tài khoản tới email.
     * Kiểm tra email chưa trùng và tôn trọng cooldown 60 giây.
     *
     * @param email Địa chỉ email nhận OTP
     */
    void sendRegistrationOtp(String email);

    /**
     * Xác thực và đánh dấu đã sử dụng mã OTP.
     *
     * @param email Email nhận mã
     * @param otpCode Mã OTP cần kiểm tra
     * @param type Loại OTP (ví dụ REGISTRATION)
     */
    void verifyAndConsumeOtp(String email, String otpCode, OtpType type);
}
