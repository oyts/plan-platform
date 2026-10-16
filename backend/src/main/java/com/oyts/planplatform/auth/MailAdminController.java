package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.TooManyRequestsException;
import com.oyts.planplatform.config.SecurityProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/mail")
@PreAuthorize("hasRole('ADMIN')")
class MailAdminController {
    private final PasswordResetMailService mail;
    private final SecurityProperties security;
    private final UserAccountRepository users;
    private final MailRequestLimiter limiter;
    MailAdminController(PasswordResetMailService mail,SecurityProperties security,UserAccountRepository users,MailRequestLimiter limiter){this.mail=mail;this.security=security;this.users=users;this.limiter=limiter;}
    record Status(boolean configured,boolean passwordResetEnabled,boolean emailVerificationEnabled) {}
    record Delivery(boolean accepted,String message) {}
    private ResponseEntity<?> reply(Object body){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(body);}
    @GetMapping ResponseEntity<?> status(){return reply(new Status(mail.configured(),security.passwordResetEnabled(),security.emailVerificationEnabled()));}
    @PostMapping("/test") ResponseEntity<?> test(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        if(!limiter.acquire("mail-test",jwt.getSubject(),request.getRemoteAddr()))throw new TooManyRequestsException("测试邮件发送过于频繁，请至少等待一分钟后重试。");
        var user=users.findById(jwt.getSubject()).filter(UserAccount::isEnabled).orElseThrow();
        boolean accepted=mail.sendTest(user.getEmail());
        return reply(new Delivery(accepted,accepted?"发件服务器已接受测试邮件，请检查当前管理员账号的收件箱和垃圾箱；这不等同于已投递到收件箱。":"邮件服务未配置或发件服务器未接受，请检查服务器受限配置。"));
    }
}
