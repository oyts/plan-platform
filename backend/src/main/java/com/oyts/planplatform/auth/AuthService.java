package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.UnauthorizedException;
import com.oyts.planplatform.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("该邮箱已经注册。");
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()), request.displayName().trim()));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim())
            .filter(UserAccount::isEnabled)
            .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
            .orElseThrow(() -> new UnauthorizedException("邮箱或密码不正确。"));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(request.refreshToken()))
            .filter(token -> token.getRevokedAt() == null && token.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new UnauthorizedException("刷新凭证无效或已经过期。"));
        stored.revoke();
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(AuthDtos.LogoutRequest request) {
        refreshTokens.findByTokenHash(hash(request.refreshToken())).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(String userId) {
        return users.findById(userId).map(this::toUser).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    private AuthDtos.TokenResponse issueTokens(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer()).issuedAt(now).expiresAt(expiresAt).subject(user.getId())
            .claim("email", user.getEmail()).claim("name", user.getDisplayName()).claim("role", user.getRole()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), now.plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS)));
        return new AuthDtos.TokenResponse(accessToken, rawRefresh, jwtProperties.accessTokenMinutes() * 60, toUser(user));
    }

    private AuthDtos.UserResponse toUser(UserAccount user) {
        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
