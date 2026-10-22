package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
class EmailChangeService {
    private final UserAccountRepository users; private final EmailChangeRequestRepository requests;
    private final PasswordResetMailService mail; private final PasswordEncoder passwords; private final MailRequestLimiter limiter;
    private final RefreshTokenRepository refresh; private final PasswordResetTokenRepository resets;
    private final EmailVerificationTokenRepository verifications; private final EntityManager entities; private final boolean enabled;
    EmailChangeService(UserAccountRepository users,EmailChangeRequestRepository requests,PasswordResetMailService mail,
                       PasswordEncoder passwords,MailRequestLimiter limiter,RefreshTokenRepository refresh,
                       PasswordResetTokenRepository resets,EmailVerificationTokenRepository verifications,EntityManager entities,
                       @Value("${app.security.email-change-enabled:false}") boolean enabled) {
        this.users=users;this.requests=requests;this.mail=mail;this.passwords=passwords;this.limiter=limiter;this.refresh=refresh;
        this.resets=resets;this.verifications=verifications;this.entities=entities;this.enabled=enabled;
    }
    record Start(@NotBlank @Size(max=200) String currentPassword,@NotBlank @Email @Size(max=190) String newEmail) {}
    record Verify(@NotNull UUID requestId,@Pattern(regexp="old|new") @NotBlank String side,@NotBlank @Size(min=60,max=100) String token) {}
    record Finish(@NotNull UUID requestId,@NotBlank @Size(max=200) String currentPassword,@AssertTrue boolean confirmed) {}
    record Status(boolean enabled,boolean configured,String requestId,String newEmail,String state,
                  boolean oldVerified,boolean newVerified,Instant expiresAt,int retryAfterSeconds) {}

    @Transactional(readOnly=true)
    Status status(String owner,String address) {
        var user=users.findById(owner).orElseThrow(()->new UnauthorizedException("请重新登录。"));
        var latest=requests.findFirstByOwnerIdOrderByCreatedAtDesc(owner).orElse(null);
        if(latest!=null&&latest.active()&&!latest.matches(user,hash(user.getPasswordHash())))
            return new Status(enabled,mail.configured(),latest.getId(),latest.getNewEmail(),"INVALIDATED",false,false,latest.getExpiresAt(),waitSeconds(owner,address));
        return toStatus(latest,waitSeconds(owner,address));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    Status start(String owner,long generation,Start input,String address) {
        checkEnabled();var user=lockUser(owner,generation);
        if(waitSeconds(owner,address)>0||!limiter.acquire("email-change",owner,address))throw new TooManyRequestsException("邮箱变更请求过于频繁，请稍后重试。");
        checkPassword(user,input.currentPassword());
        String target=input.newEmail().strip().toLowerCase(Locale.ROOT);
        if(target.equalsIgnoreCase(user.getEmail()))throw new IllegalArgumentException("新邮箱需要与当前邮箱不同。");
        if(users.existsByEmailIgnoreCase(target))throw unavailable();
        if(!mail.configured())throw new MailDeliveryException();
        String oldRaw=raw(),newRaw=raw();
        var request=new EmailChangeRequest(user,target,hash(user.getPasswordHash()),hash(oldRaw),hash(newRaw),Instant.now().plusSeconds(1800));
        // Both messages must be accepted before replacing a previous valid request.
        // A partial SMTP failure leaves the new links unusable; no secrets are returned.
        if(!mail.sendEmailChange(user.getEmail(),request.getId(),"old",oldRaw)
            ||!mail.sendEmailChange(target,request.getId(),"new",newRaw))throw new MailDeliveryException();
        requests.findByOwnerIdAndConsumedAtIsNullAndCancelledAtIsNull(owner).forEach(EmailChangeRequest::cancel);
        requests.save(request);return toStatus(request,60);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    void verify(Verify input,String address) {
        checkEnabled();
        if(!limiter.acquire("email-change-verify",input.requestId()+":"+input.side(),address))throw new TooManyRequestsException("验证尝试过于频繁，请稍后重试。");
        var found=requests.findById(input.requestId().toString()).orElseThrow(EmailChangeService::invalid);
        var user=users.lockById(found.getOwnerId()).orElseThrow(EmailChangeService::invalid);
        entities.refresh(found,LockModeType.PESSIMISTIC_WRITE);
        boolean old=input.side().equals("old");String expected=old?found.getOldTokenHash():found.getNewTokenHash();
        if(!found.active()||!found.matches(user,hash(user.getPasswordHash()))||found.verified(old)
            ||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),hash(input.token()).getBytes(StandardCharsets.US_ASCII)))throw invalid();
        found.verify(old);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    void cancel(String owner,long generation,String requestId) {
        lockUser(owner,generation);
        var request=requests.findByIdAndOwnerId(requestId,owner).orElseThrow(()->new NotFoundException("邮箱变更请求不存在。"));
        if("CHANGED".equals(request.state()))throw new ConflictException("邮箱已经变更，请使用新邮箱登录。");request.cancel();
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    void finish(String owner,long generation,Finish input,String address) {
        checkEnabled();var user=lockUser(owner,generation);
        if(!limiter.acquire("email-change-finish",owner,address))throw new TooManyRequestsException("确认尝试过于频繁，请稍后重试。");
        checkPassword(user,input.currentPassword());
        var request=requests.findByIdAndOwnerId(input.requestId().toString(),owner).orElseThrow(()->new NotFoundException("邮箱变更请求不存在。"));
        if(!request.active()||!request.matches(user,hash(user.getPasswordHash())))throw invalid();
        if(!request.verified(true)||!request.verified(false))throw new ConflictException("请先分别完成原邮箱与新邮箱验证。");
        if(users.existsByEmailIgnoreCase(request.getNewEmail()))throw unavailable();
        try {
            user.changeEmail(request.getNewEmail());request.consume();
            // Claim the unique address before any query can implicitly flush it outside this guard.
            entities.flush();
            requests.findByOwnerIdAndConsumedAtIsNullAndCancelledAtIsNull(owner).forEach(EmailChangeRequest::cancel);
            Instant now=Instant.now();
            refresh.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(owner,now).forEach(RefreshToken::revoke);
            resets.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(owner,now).forEach(PasswordResetToken::revoke);
            verifications.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(owner,now).forEach(EmailVerificationToken::revoke);
            entities.createNativeQuery("update native_reminder_devices set enabled=false where owner_id=:owner").setParameter("owner",owner).executeUpdate();
            entities.flush();
        } catch(org.springframework.dao.DataIntegrityViolationException|org.hibernate.exception.ConstraintViolationException failure) {throw unavailable();}
    }
    private UserAccount lockUser(String owner,long generation) {
        var user=users.lockById(owner).orElseThrow(()->new UnauthorizedException("请重新登录。"));entities.refresh(user);
        if(!user.isEnabled()||user.getAuthGeneration()!=generation)throw new UnauthorizedException("会话已撤销，请重新登录。");return user;
    }
    private void checkPassword(UserAccount user,String password){if(!passwords.matches(password,user.getPasswordHash()))throw new IllegalArgumentException("当前密码不正确。");}
    private void checkEnabled(){if(!enabled)throw new ForbiddenException("服务器暂未启用邮箱变更。");}
    private int waitSeconds(String owner,String address) {
        int wait=limiter.retryAfterSeconds("email-change",owner,address);
        var latest=requests.findFirstByOwnerIdOrderByCreatedAtDesc(owner).orElse(null);
        if(latest!=null)wait=Math.max(wait,(int)Math.max(0,java.time.Duration.between(Instant.now(),latest.getCreatedAt().plusSeconds(60)).getSeconds()+1));
        if(requests.countByOwnerIdAndCreatedAtAfter(owner,Instant.now().minusSeconds(86400))>=5)wait=Math.max(wait,3600);
        return wait;
    }
    private Status toStatus(EmailChangeRequest request,int wait){return new Status(enabled,mail.configured(),request==null?null:request.getId(),request==null?null:request.getNewEmail(),request==null?"NONE":request.state(),request!=null&&request.verified(true),request!=null&&request.verified(false),request==null?null:request.getExpiresAt(),wait);}
    private static UnauthorizedException invalid(){return new UnauthorizedException("验证无效、已使用或已经过期，请回到账号设置核对。");}
    private static ConflictException unavailable(){return new ConflictException("暂时无法使用该邮箱完成变更，请核对地址或稍后重试。");}
    private static String raw(){return UUID.randomUUID()+"."+UUID.randomUUID();}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
