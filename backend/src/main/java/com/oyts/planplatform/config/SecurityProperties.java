package com.oyts.planplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(boolean registrationEnabled, String refreshCookieName,
                                 boolean refreshCookieSecure, String refreshCookieSameSite,
                                 int maxLoginAttempts, long loginWindowSeconds, int maxSessions,
                                 boolean passwordResetEnabled, long passwordResetMinutes) {
    public SecurityProperties {
        if (refreshCookieName == null || refreshCookieName.isBlank()) refreshCookieName = "plan_refresh";
        if (refreshCookieSameSite == null || refreshCookieSameSite.isBlank()) refreshCookieSameSite = "Strict";
        if (maxLoginAttempts < 1) maxLoginAttempts = 5;
        if (loginWindowSeconds < 60) loginWindowSeconds = 900;
        if (maxSessions < 1) maxSessions = 5;
        if (passwordResetMinutes < 10) passwordResetMinutes = 60;
    }
}
