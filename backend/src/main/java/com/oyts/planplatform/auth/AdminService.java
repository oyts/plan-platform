package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminService {
    private final UserAccountRepository users; private final InvitationRepository invitations; private final PasswordResetMailService mail; private final SecurityProperties security;private final org.springframework.jdbc.core.JdbcTemplate db;
    AdminService(UserAccountRepository users, InvitationRepository invitations, PasswordResetMailService mail, SecurityProperties security,org.springframework.jdbc.core.JdbcTemplate db) { this.users = users; this.invitations = invitations; this.mail = mail; this.security = security;this.db=db; }
    @Transactional(readOnly = true) List<AuthDtos.InvitationResponse> invitations() { return invitations.findTop200ByOrderByCreatedAtDesc().stream().map(item -> response(item, null,false)).toList(); }
    private void lock(){db.queryForObject("SELECT id FROM admin_invitation_control WHERE id=1 FOR UPDATE",Integer.class);}
    @Transactional AuthDtos.InvitationResponse create(String userId, AuthDtos.InvitationRequest request) {
        if (!security.invitationEnabled()) throw new ConflictException("服务器未启用邀请制注册。");
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        int hours = request.expiresInHours() == null ? (int) security.invitationHours() : request.expiresInHours();
        if (hours < 1 || hours > 720) throw new IllegalArgumentException("邀请有效期必须在 1 到 720 小时之间。");
        String operation=request.operationKey()==null?UUID.randomUUID().toString():request.operationKey();
        if(!operation.matches("[A-Za-z0-9._:-]{8,100}"))throw new IllegalArgumentException("邀请请求编号无效。");
        lock();String fingerprint=hash(email+"\n"+hours);
        var prior=db.query("SELECT fingerprint,invitation_id FROM admin_invitation_operations WHERE actor_id=? AND operation_key=?",(r,n)->new String[]{r.getString(1),r.getString(2)},userId,operation);
        if(!prior.isEmpty()){
            if(!prior.getFirst()[0].equals(fingerprint))throw new ConflictException("同一邀请编号不能改为其他邮箱或有效期。");
            return response(invitations.findById(prior.getFirst()[1]).orElseThrow(()->new NotFoundException("邀请记录不存在。")),null,true);
        }
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("该邮箱已经拥有账号。");
        // Keep Hibernate's UTC Instant mapping; default JDBC timestamp conversion
        // can shift this JPA-created value with MySQL serverTimezone settings.
        var latest=invitations.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if(latest.isPresent()&&latest.get().getCreatedAt().plusSeconds(60).isAfter(Instant.now()))throw new com.oyts.planplatform.common.TooManyRequestsException("该邮箱处于60秒邀请冷却期，请稍后核对记录；不会重复发信或提前废除旧链接。");
        invitations.findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(email, Instant.now()).forEach(Invitation::revoke);
        UserAccount creator = users.findById(userId).orElseThrow(() -> new NotFoundException("管理员不存在。"));
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        Invitation invitation = invitations.saveAndFlush(new Invitation(email, hash(rawToken), Instant.now().plus(hours, ChronoUnit.HOURS), creator));
        // Allocate the durable operation before attempting mail; never persist the raw link/token.
        db.update("INSERT INTO admin_invitation_operations(actor_id,operation_key,fingerprint,invitation_id,delivery_status,created_at) VALUES (?,?,?,?,?,?)",userId,operation,fingerprint,invitation.getId(),"FALLBACK",java.sql.Timestamp.from(Instant.now()));
        boolean mailed = mail.sendInvitation(email, rawToken);
        db.update("UPDATE admin_invitation_operations SET delivery_status=? WHERE actor_id=? AND operation_key=?",mailed?"SENT":"FALLBACK",userId,operation);
        return response(invitation, mailed ? null : mail.invitationLink(email, rawToken),false);
    }
    @Transactional void revoke(String id) {lock(); invitations.findById(id).orElseThrow(() -> new NotFoundException("邀请不存在。")).revoke(); }
    private AuthDtos.InvitationResponse response(Invitation item, String registrationUrl,boolean replayed) {
        var status=db.query("SELECT delivery_status FROM admin_invitation_operations WHERE invitation_id=?",(r,n)->r.getString(1),item.getId());
        return new AuthDtos.InvitationResponse(item.getId(), item.getEmail(), item.getExpiresAt(), item.getAcceptedAt(), item.getRevokedAt(), item.getCreatedBy().getDisplayName(), registrationUrl,status.isEmpty()?"UNKNOWN":status.getFirst(),replayed,item.getCreatedAt().plusSeconds(60));
    }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
}
