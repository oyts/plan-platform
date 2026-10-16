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
        String link = publicUrl + "/reset-password?email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
            + "&token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return send(user.getEmail(), "计划平台：重置密码", "你收到了重置计划平台密码的请求。请在有效期内打开以下链接设置新密码：\n" + link
            + "\n\n如果不是你本人操作，请忽略本邮件。此链接仅可使用一次。");
    }
    boolean sendInvitation(String email, String rawToken) {
        return send(email, "计划平台：邀请注册", "你被邀请使用计划平台。请在有效期内打开以下链接创建账号：\n" + invitationLink(email, rawToken)
            + "\n\n此链接仅可使用一次。若不是你本人收到，请忽略本邮件。");
    }
    boolean sendEmailVerification(UserAccount user, String rawToken) {
        String link = publicUrl + "/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return send(user.getEmail(), "计划平台：验证邮箱", "请在有效期内打开以下链接验证你的计划平台邮箱：\n" + link + "\n\n此链接仅可使用一次。");
    }
    String invitationLink(String email, String rawToken) {
        return publicUrl + "/register?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&invite=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
    boolean configured() { return senders.getIfAvailable()!=null && !from.isBlank(); }
    boolean sendTest(String recipient) { return send(recipient,"计划舱：邮件服务测试","这是一封由计划舱管理员主动请求的邮件测试。它不包含密码、重置链接、邀请链接或私人计划。收到后请回到管理员页面核对。"); }
    private boolean send(String recipient, String subject, String text) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) return false;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(text);
        try { sender.send(message); return true; }
        catch (MailException exception) { return false; }
    }
}
