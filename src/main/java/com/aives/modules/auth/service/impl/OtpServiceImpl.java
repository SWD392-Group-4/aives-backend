package com.aives.modules.auth.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.common.util.RandomIdGenerator;
import com.aives.integration.mail.MailMessage;
import com.aives.integration.mail.MailService;
import com.aives.modules.auth.entity.OtpVerification;
import com.aives.modules.auth.enums.OtpType;
import com.aives.modules.auth.repository.OtpVerificationRepository;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.auth.service.OtpService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final int OTP_EXPIRE_MINUTES = 5;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;

    private final OtpVerificationRepository otpVerificationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final RandomIdGenerator randomIdGenerator;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void sendRegistrationOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email này đã được sử dụng");
        }

        var latestOtp = otpVerificationRepository
                .findTopByEmailAndTypeOrderByCreatedAtDesc(normalizedEmail, OtpType.REGISTRATION);
        if (latestOtp.isPresent()) {
            LocalDateTime createdAt = latestOtp.get().getCreatedAt();
            if (createdAt != null && createdAt.plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.OTP_RATE_LIMITED);
            }
        }

        String otpCode = String.format("%06d", random.nextInt(1_000_000));

        OtpVerification otp = OtpVerification.builder()
                .email(normalizedEmail)
                .otpCode(otpCode)
                .type(OtpType.REGISTRATION)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES))
                .attempts(0)
                .verified(false)
                .build();
        otp.setId(randomIdGenerator.next("OV"));
        otpVerificationRepository.save(otp);

        sendEmail(normalizedEmail, otpCode);
    }

    @Override
    @Transactional
    public void sendPasswordResetOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        if (!userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này");
        }

        var latestOtp = otpVerificationRepository
                .findTopByEmailAndTypeOrderByCreatedAtDesc(normalizedEmail, OtpType.PASSWORD_RESET);
        if (latestOtp.isPresent()) {
            LocalDateTime createdAt = latestOtp.get().getCreatedAt();
            if (createdAt != null && createdAt.plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.OTP_RATE_LIMITED);
            }
        }

        String otpCode = String.format("%06d", random.nextInt(1_000_000));

        OtpVerification otp = OtpVerification.builder()
                .email(normalizedEmail)
                .otpCode(otpCode)
                .type(OtpType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES))
                .attempts(0)
                .verified(false)
                .build();
        otp.setId(randomIdGenerator.next("OV"));
        otpVerificationRepository.save(otp);

        sendPasswordResetEmail(normalizedEmail, otpCode);
    }

    @Override
    @Transactional
    public void verifyAndConsumeOtp(String email, String otpCode, OtpType type) {
        String normalizedEmail = email.trim().toLowerCase();
        String normalizedCode = otpCode != null ? otpCode.trim() : "";

        OtpVerification otp = otpVerificationRepository
                .findTopByEmailAndTypeAndVerifiedFalseOrderByCreatedAtDesc(normalizedEmail, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_NOT_FOUND, "Không tìm thấy mã OTP xác thực cho email này hoặc mã đã được sử dụng"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }

        if (!otp.getOtpCode().equals(normalizedCode)) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpVerificationRepository.save(otp);

            if (otp.getAttempts() >= MAX_ATTEMPTS) {
                throw new BusinessException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            }
            int remaining = MAX_ATTEMPTS - otp.getAttempts();
            throw new BusinessException(ErrorCode.OTP_INVALID, "Mã xác thực OTP không đúng. Bạn còn " + remaining + " lần thử");
        }

        otp.setVerified(true);
        otpVerificationRepository.save(otp);
    }

    private void sendEmail(String toEmail, String otpCode) {
        String subject = "[AIVES] Mã xác thực đăng ký tài khoản: " + otpCode;

        String text = """
                Xin chào,

                Bạn đang thực hiện đăng ký tài khoản trên hệ thống thi vấn đáp AIVES.

                Mã xác thực (OTP) của bạn là: %s

                Mã có hiệu lực trong vòng %d phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.
                Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.

                AIVES - FPT University
                """.formatted(otpCode, OTP_EXPIRE_MINUTES);

        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1f2937;max-width:560px;margin:0 auto;padding:24px;border:1px solid #e5e7eb;border-radius:12px;background:#ffffff">
                  <h2 style="color:#4338ca;margin:0 0 16px;text-align:center">Xác thực tài khoản AIVES</h2>
                  <p>Xin chào,</p>
                  <p>Bạn đang thực hiện đăng ký tài khoản trên hệ thống thi vấn đáp <b>AIVES</b>. Vui lòng sử dụng mã xác thực (OTP) dưới đây để hoàn tất đăng ký:</p>
                  <div style="background:#eef2ff;border:1px dashed #6366f1;border-radius:8px;padding:16px;text-align:center;margin:24px 0">
                    <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#4338ca;display:inline-block">%s</span>
                  </div>
                  <p style="color:#4b5563;font-size:14px">Mã xác thực có hiệu lực trong <b>%d phút</b>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai để bảo vệ tài khoản của bạn.</p>
                  <p style="color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;padding-top:16px;margin-top:24px;text-align:center">AIVES - AI-powered Viva Exam System · FPT University</p>
                </div>
                """.formatted(otpCode, OTP_EXPIRE_MINUTES);

        try {
            mailService.send(new MailMessage(toEmail, subject, text, html));
        } catch (MailException ex) {
            log.error("Không gửi được email OTP tới {}: {}", toEmail, ex.getMessage());
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED,
                    "Không gửi được email chứa mã OTP tới " + toEmail + ". Vui lòng kiểm tra lại địa chỉ email và cấu hình hòm thư.");
        }
    }

    private void sendPasswordResetEmail(String toEmail, String otpCode) {
        String subject = "[AIVES] Mã xác thực đặt lại mật khẩu: " + otpCode;

        String text = """
                Xin chào,

                Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản AIVES.

                Mã xác thực (OTP) của bạn là: %s

                Mã có hiệu lực trong vòng %d phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.
                Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.

                AIVES - FPT University
                """.formatted(otpCode, OTP_EXPIRE_MINUTES);

        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1f2937;max-width:560px;margin:0 auto;padding:24px;border:1px solid #e5e7eb;border-radius:12px;background:#ffffff">
                  <h2 style="color:#4338ca;margin:0 0 16px;text-align:center">Đặt lại mật khẩu AIVES</h2>
                  <p>Xin chào,</p>
                  <p>Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản hệ thống thi vấn đáp <b>AIVES</b>. Vui lòng sử dụng mã xác thực (OTP) dưới đây để tiến hành đặt lại mật khẩu:</p>
                  <div style="background:#eef2ff;border:1px dashed #6366f1;border-radius:8px;padding:16px;text-align:center;margin:24px 0">
                    <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#4338ca;display:inline-block">%s</span>
                  </div>
                  <p style="color:#4b5563;font-size:14px">Mã xác thực có hiệu lực trong <b>%d phút</b>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai để bảo vệ tài khoản của bạn.</p>
                  <p style="color:#6b7280;font-size:13px;border-top:1px solid #f3f4f6;padding-top:16px;margin-top:24px;text-align:center">AIVES - AI-powered Viva Exam System · FPT University</p>
                </div>
                """.formatted(otpCode, OTP_EXPIRE_MINUTES);

        try {
            mailService.send(new MailMessage(toEmail, subject, text, html));
        } catch (MailException ex) {
            log.error("Không gửi được email OTP đặt lại mật khẩu tới {}: {}", toEmail, ex.getMessage());
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED,
                    "Không gửi được email chứa mã OTP tới " + toEmail + ". Vui lòng kiểm tra lại địa chỉ email và cấu hình hòm thư.");
        }
    }
}

