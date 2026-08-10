package com.oyts.planplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String issuer, String secret, long accessTokenMinutes, long refreshTokenDays) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("JWT secret 至少需要 32 个字符。");
    }
}
