package com.aives.modules.auth.service.impl;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.integration.mail.MailMessage;
import com.aives.integration.mail.MailService;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.service.AccountMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMailServiceImpl implements AccountMailService {

    private static final String SUBJECT = "[AIVES] Tài khoản của bạn đã được tạo";

    private final MailService mailService;

    // Dia chi FE, dung de chen link dang nhap vao email
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public boolean sendAccountCreated(User user, String temporaryPassword) {
        String loginUrl = frontendUrl.replaceAll("/+$", "") + "/login";
        String roleLabel = user.getRoleName().getDescription();

        String text = """
                Xin chào %s,

                Quản trị viên đã tạo cho bạn một tài khoản %s trên hệ thống thi vấn đáp AIVES.

                Email đăng nhập: %s
                Mật khẩu tạm: %s

                Đăng nhập tại: %s
                Vì lý do bảo mật, bạn sẽ được yêu cầu đổi mật khẩu ngay trong lần đăng nhập đầu tiên.
                Không chia sẻ email này cho người khác.

                AIVES - FPT University
                """.formatted(user.getFullName(), roleLabel, user.getEmail(), temporaryPassword, loginUrl);

        // Ho ten do admin nhap nen phai escape truoc khi dua vao HTML
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1f2937;max-width:560px">
                  <h2 style="color:#4338ca;margin:0 0 16px">Chào mừng bạn đến với AIVES</h2>
                  <p>Xin chào <b>%s</b>,</p>
                  <p>Quản trị viên đã tạo cho bạn một tài khoản <b>%s</b> trên hệ thống thi vấn đáp AIVES.</p>
                  <table style="border-collapse:collapse;margin:16px 0">
                    <tr><td style="padding:6px 16px 6px 0;color:#6b7280">Email đăng nhập</td><td><b>%s</b></td></tr>
                    <tr><td style="padding:6px 16px 6px 0;color:#6b7280">Mật khẩu tạm</td>
                        <td><code style="font-size:17px;background:#eef2ff;padding:4px 10px;border-radius:6px">%s</code></td></tr>
                  </table>
                  <p><a href="%s" style="display:inline-block;background:#4338ca;color:#ffffff;text-decoration:none;padding:10px 22px;border-radius:8px">Đăng nhập</a></p>
                  <p>Vì lý do bảo mật, bạn sẽ được yêu cầu <b>đổi mật khẩu</b> ngay trong lần đăng nhập đầu tiên. Không chia sẻ email này cho người khác.</p>
                  <p style="color:#6b7280;font-size:13px;margin-top:24px">AIVES - FPT University</p>
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(user.getFullName()),
                HtmlUtils.htmlEscape(roleLabel),
                HtmlUtils.htmlEscape(user.getEmail()),
                HtmlUtils.htmlEscape(temporaryPassword),
                HtmlUtils.htmlEscape(loginUrl));

        try {
            mailService.send(new MailMessage(user.getEmail(), SUBJECT, text, html));
        } catch (MailException ex) {
            // Khong ghi mat khau tam vao log
            log.error("Khong gui duoc email tai khoan toi {}: {}", user.getEmail(), ex.getMessage());
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED,
                    "Không gửi được email tới " + user.getEmail() + " nên tài khoản chưa được tạo. "
                            + "Vui lòng kiểm tra địa chỉ email và cấu hình gửi mail rồi thử lại");
        }
        return mailService.isEnabled();
    }
}
