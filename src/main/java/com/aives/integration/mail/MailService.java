package com.aives.integration.mail;

/**
 * Cong gui email cua he thong. Module nghiep vu chi goi interface nay,
 * khong biet ben duoi la SMTP (SmtpMailService) hay chi ghi log (LogOnlyMailService).
 */
public interface MailService {

    /** true: email duoc gui that qua SMTP. false: chi ghi ra log (chua cau hinh gui mail). */
    boolean isEnabled();

    /**
     * Gui 1 email. Nem org.springframework.mail.MailException neu khong gui duoc.
     */
    void send(MailMessage message);
}
