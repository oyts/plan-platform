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
    private final UserAccountRepository users; private final InvitationRepository invitations; private final PasswordResetMailService mail; private final SecurityProperties security;
    AdminService(UserAccountRepository users, InvitationRepository invitations, PasswordResetMailService mail, SecurityProperties security) { this.users = users; this.invitations = invitations; this.mail = mail; this.security = security; }
    @Transactional(readOnly = true) List<AuthDtos.InvitationResponse> invitations() { return invitations.findByOrderByCreatedAtDesc().stream().map(item -> response(item, null)).toList(); }
    @Transactional AuthDtos.InvitationResponse create(String userId, AuthDtos.InvitationRequest request) {
        if (!security.invitationEnabled()) throw new ConflictException("服务器未启用邀请制注册。");
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("该邮箱已经拥有账号。");
        invitations.findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(email, Instant.now()).forEach(Invitation::revoke);
        UserAccount creator = users.findById(userId).orElseThrow(() -> new NotFoundException("管理员不存在。"));
        int hours = request.expiresInHours() == null ? (int) security.invitationHours() : request.expiresInHours();
        if (hours < 1 || hours > 720) throw new IllegalArgumentException("邀请有效期必须在 1 到 720 小时之间。");
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        Invitation invitation = invitations.save(new Invitation(email, hash(rawToken), Instant.now().plus(hours, ChronoUnit.HOURS), creator));
        boolean mailed = mail.sendInvitation(email, rawToken);
        return response(invitation, mailed ? null : mail.invitationLink(email, rawToken));
    }
    @Transactional void revoke(String id) { invitations.findById(id).orElseThrow(() -> new NotFoundException("邀请不存在。")).revoke(); }
    private AuthDtos.InvitationResponse response(Invitation item, String registrationUrl) { return new AuthDtos.InvitationResponse(item.getId(), item.getEmail(), item.getExpiresAt(), item.getAcceptedAt(), item.getRevokedAt(), item.getCreatedBy().getDisplayName(), registrationUrl); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
}
