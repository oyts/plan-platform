package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    protected EmailVerificationToken() {}
    public EmailVerificationToken(UserAccount user, String tokenHash, Instant expiresAt) { this.user = user; this.tokenHash = tokenHash; this.expiresAt = expiresAt; }
    public void consume() { usedAt = Instant.now(); } public void revoke() { usedAt = Instant.now(); }
    public UserAccount getUser() { return user; } public Instant getExpiresAt() { return expiresAt; } public Instant getUsedAt() { return usedAt; }
}
