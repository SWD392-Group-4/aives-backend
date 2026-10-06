package com.aives.integration.mail;

/**
 * Noi dung 1 email. textBody dung cho ung dung mail khong hien HTML (va de ghi log khi tat gui mail).
 */
public record MailMessage(String to, String subject, String textBody, String htmlBody) {
}
