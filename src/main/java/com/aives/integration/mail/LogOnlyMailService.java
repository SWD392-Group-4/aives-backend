package com.aives.integration.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Dung khi CHUA cau hinh gui mail (app.mail.enabled = false, mac dinh): khong gui gi ca,
 * chi in noi dung email ra console de nguoi dev doc. Khong dung cho moi truong that.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogOnlyMailService implements MailService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void send(MailMessage message) {
        log.warn("""
                [MAIL CHUA BAT - app.mail.enabled=false] Email KHONG duoc gui di, noi dung:
                To: {}
                Subject: {}
                {}""", message.to(), message.subject(), message.textBody());
    }
}
