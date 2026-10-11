package com.aives.integration.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Gui email that qua SMTP (cau hinh spring.mail.* trong application.yml).
 * Chi duoc tao khi app.mail.enabled = true.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name:AIVES}")
    private String fromName;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(MailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            // multipart = true de gui kem ca ban text va ban HTML
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Không tạo được email", ex);
        }
        mailSender.send(mimeMessage);
    }
}
