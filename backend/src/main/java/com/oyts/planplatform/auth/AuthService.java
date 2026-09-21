package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.ForbiddenException;
import com.oyts.planplatform.common.UnauthorizedException;
import com.oyts.planplatform.config.JwtProperties;
import com.oyts.planplatform.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final InvitationRepository invitations;
    private final EmailVerificationTokenRepository emailVerificationTokens;
    private final PasswordResetMailService passwordResetMail;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;
    private final LoginAttemptService loginAttempts;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository passwordResetTokens,
                       InvitationRepository invitations, EmailVerificationTokenRepository emailVerificationTokens,
                       PasswordResetMailService passwordResetMail, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, JwtProperties jwtProperties, SecurityProperties securityProperties,
                       LoginAttemptService loginAttempts) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordResetTokens = passwordResetTokens;
        this.invitations = invitations;
        this.emailVerificationTokens = emailVerificationTokens;
        this.passwordResetMail = passwordResetMail;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.securityProperties = securityProperties;
        this.loginAttempts = loginAttempts;
    }

    @Transactional
    public IssuedTokens register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("该邮箱已经注册。");
        Invitation invitation = null;
        if (!securityProperties.registrationEnabled()) {
            if (!securityProperties.invitationEnabled() || request.invitationToken() == null || request.invitationToken().isBlank()) throw new ForbiddenException("当前仅接受有效邀请注册链接注册。");
            invitation = invitations.findByTokenHashAndEmailIgnoreCase(hash(request.invitationToken()), email)
                .filter(candidate -> candidate.isActive(Instant.now())).orElseThrow(() -> new ForbiddenException("邀请链接无效、已使用或已经过期。"));
        }
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()), request.displayName().trim()));
        if (invitation != null) invitation.accept();
        if (securityProperties.emailVerificationEnabled()) sendEmailVerification(user);
        return issueTokens(user);
    }

    @Transactional
    public IssuedTokens login(AuthDtos.LoginRequest request, String clientAddress) {
        loginAttempts.check(request.email(), clientAddress);
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim()).filter(UserAccount::isEnabled)
            .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash())).orElse(null);
        if (user == null) {
            loginAttempts.failed(request.email(), clientAddress);
            throw new UnauthorizedException("邮箱或密码不正确。");
        }
        loginAttempts.succeeded(request.email(), clientAddress);
        return issueTokens(user);
    }

    @Transactional
    public IssuedTokens refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new UnauthorizedException("刷新凭证无效或已经过期。");
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
            .filter(token -> token.getRevokedAt() == null && token.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new UnauthorizedException("刷新凭证无效或已经过期。"));
        stored.revoke();
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.SessionResponse> sessions(String userId, String currentRawToken) {
        String currentHash = currentRawToken == null ? "" : hash(currentRawToken);
        return refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now()).stream()
            .map(token -> new AuthDtos.SessionResponse(token.getId(), token.getCreatedAt(), token.getExpiresAt(), token.getTokenHash().equals(currentHash))).toList();
    }

    @Transactional
    public boolean revokeSession(String userId, String sessionId, String currentRawToken) {
        RefreshToken token = refreshTokens.findByIdAndUserId(sessionId, userId).orElse(null);
        if (token == null) return false;
        boolean current = currentRawToken != null && token.getTokenHash().equals(hash(currentRawToken));
        token.revoke();
        return current;
    }

    @Transactional
    public void logoutAll(String userId) {
        revokeAllSessions(userId);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(String userId) {
        return users.findById(userId).map(this::toUser).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    @Transactional
    public AuthDtos.UserResponse updateProfile(String userId, AuthDtos.ProfileRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        user.updateDisplayName(request.displayName());
        return toUser(user);
    }

    /** A password change ends every refresh session, so a stolen long-lived cookie cannot survive the change. */
    @Transactional
    public void changePassword(String userId, AuthDtos.ChangePasswordRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) throw new UnauthorizedException("当前密码不正确。" );
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        revokeAllSessions(userId);
    }

    /** Always returns normally to prevent account enumeration. A reset is possible only with configured SMTP delivery. */
    @Transactional
    public void requestPasswordReset(AuthDtos.PasswordResetRequest request) {
        if (!securityProperties.passwordResetEnabled()) return;
        users.findByEmailIgnoreCase(request.email().trim()).filter(UserAccount::isEnabled).ifPresent(user -> {
            String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
            if (!passwordResetMail.send(user, rawToken)) return;
            passwordResetTokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(user.getId(), Instant.now()).forEach(PasswordResetToken::revoke);
            passwordResetTokens.save(new PasswordResetToken(user, hash(rawToken), Instant.now().plus(securityProperties.passwordResetMinutes(), ChronoUnit.MINUTES)));
        });
    }

    @Transactional
    public void confirmPasswordReset(AuthDtos.PasswordResetConfirmRequest request) {
        PasswordResetToken token = passwordResetTokens.findByTokenHashAndUserEmailIgnoreCase(hash(request.token()), request.email().trim())
            .filter(item -> item.getUsedAt() == null && item.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new UnauthorizedException("重置链接无效、已使用或已经过期。"));
        UserAccount user = token.getUser();
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        token.consume();
        revokeAllSessions(user.getId());
    }

    @Transactional
    public void requestEmailVerification(String userId) {
        if (!securityProperties.emailVerificationEnabled()) throw new ForbiddenException("服务器尚未启用邮箱验证。");
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        if (!user.isEmailVerified()) sendEmailVerification(user);
    }

    @Transactional
    public void confirmEmailVerification(AuthDtos.EmailVerificationConfirmRequest request) {
        EmailVerificationToken token = emailVerificationTokens.findByTokenHash(hash(request.token()))
            .filter(item -> item.getUsedAt() == null && item.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new UnauthorizedException("验证链接无效、已使用或已经过期。"));
        token.getUser().verifyEmail(); token.consume();
    }

    private IssuedTokens issueTokens(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer()).issuedAt(now).expiresAt(expiresAt).subject(user.getId())
            .claim("email", user.getEmail()).claim("name", user.getDisplayName()).claim("role", user.getRole()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        List<RefreshToken> active = refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), now);
        for (int index = securityProperties.maxSessions() - 1; index < active.size(); index++) active.get(index).revoke();
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), now.plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS)));
        return new IssuedTokens(new AuthDtos.TokenResponse(accessToken, jwtProperties.accessTokenMinutes() * 60, toUser(user)), rawRefresh);
    }

    private void revokeAllSessions(String userId) {
        refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now()).forEach(RefreshToken::revoke);
    }

    private AuthDtos.UserResponse toUser(UserAccount user) {
        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isEmailVerified());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void sendEmailVerification(UserAccount user) {
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        if (!passwordResetMail.sendEmailVerification(user, rawToken)) return;
        emailVerificationTokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfter(user.getId(), Instant.now()).forEach(EmailVerificationToken::revoke);
        emailVerificationTokens.save(new EmailVerificationToken(user, hash(rawToken), Instant.now().plus(securityProperties.emailVerificationMinutes(), ChronoUnit.MINUTES)));
    }

    record IssuedTokens(AuthDtos.TokenResponse response, String refreshToken) {}
}
