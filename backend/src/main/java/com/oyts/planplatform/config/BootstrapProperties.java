package com.oyts.planplatform.config;

import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional one-time authorization, not a password or an open registration switch. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String tokenHash, Instant expiresAt) {
    public BootstrapProperties {
        if (tokenHash == null) tokenHash = "";
        if (!tokenHash.isBlank() && (!tokenHash.matches("[a-f0-9]{64}") || expiresAt == null))
            throw new IllegalArgumentException("Bootstrap requires a SHA256 token hash and expiration.");
    }
    public boolean active() { return !tokenHash.isBlank() && expiresAt != null && expiresAt.isAfter(Instant.now()); }
}
