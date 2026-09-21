package com.oyts.planplatform.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends no secrets to logs or HTTP callers. Production delivery is enabled only when SMTP is configured. */
@Service
class PasswordResetMailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final String publicUrl;
    private final String from;
    PasswordResetMailService(ObjectProvider<JavaMailSender> senders,
                             @Value("${app.public-url:http://localhost:43128}") String publicUrl,
                             @Value("${app.mail-from:}") String from) { this.senders = senders; this.publicUrl = publicUrl.replaceAll("/+$", ""); this.from = from; }

    boolean send(UserAccount user, String rawToken) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) return false;
        String link = publicUrl + "/reset-password?email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
            + "&token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(user.getEmail()); message.setSubject("计划平台：重置密码");
        message.setText("你收到了重置计划平台密码的请求。请在有效期内打开以下链接设置新密码：\n" + link
            + "\n\n如果不是你本人操作，请忽略本邮件。此链接仅可使用一次。");
        try { sender.send(message); return true; }
        catch (MailException exception) { return false; }
    }
}
