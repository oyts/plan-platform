package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount extends BaseEntity {
    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String role = "USER";

    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name="auth_generation",nullable=false) private long authGeneration;
    @Column(name = "email_verified_at") private Instant emailVerifiedAt;

    protected UserAccount() {}

    public UserAccount(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }
    public void updateDisplayName(String displayName) { this.displayName = displayName.trim(); }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }
    public void verifyEmail() { emailVerifiedAt = Instant.now(); }
    public void makeAdmin() { role = "ADMIN"; }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public long getAuthGeneration() { return authGeneration; }
    public void setMemberEnabled(boolean value) { enabled=value;authGeneration++; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
}
