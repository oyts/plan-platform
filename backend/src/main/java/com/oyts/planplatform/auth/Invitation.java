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
@Table(name = "invitations")
public class Invitation extends BaseEntity {
    @Column(nullable = false, length = 190) private String email;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private UserAccount createdBy;
    protected Invitation() {}
    public Invitation(String email, String tokenHash, Instant expiresAt, UserAccount createdBy) { this.email = email.trim().toLowerCase(); this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.createdBy = createdBy; }
    public boolean isActive(Instant now) { return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now); }
    public void accept() { acceptedAt = Instant.now(); }
    public void revoke() { revokedAt = Instant.now(); }
    public String getEmail() { return email; } public String getTokenHash() { return tokenHash; } public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; } public Instant getRevokedAt() { return revokedAt; } public UserAccount getCreatedBy() { return createdBy; }
}
