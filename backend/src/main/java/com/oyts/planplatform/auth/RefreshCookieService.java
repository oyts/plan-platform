package com.oyts.planplatform.auth;

import com.oyts.planplatform.config.JwtProperties;
import com.oyts.planplatform.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class RefreshCookieService {
    private static final String COOKIE_PATH = "/api/auth";
    private final SecurityProperties security;
    private final JwtProperties jwt;

    RefreshCookieService(SecurityProperties security, JwtProperties jwt) { this.security = security; this.jwt = jwt; }

    String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> security.refreshCookieName().equals(cookie.getName()))
            .map(Cookie::getValue).findFirst().orElse(null);
    }

    void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(jwt.refreshTokenDays())).toString());
    }

    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(security.refreshCookieName(), value).httpOnly(true).secure(security.refreshCookieSecure())
            .sameSite(security.refreshCookieSameSite()).path(COOKIE_PATH).maxAge(maxAge).build();
    }
}
