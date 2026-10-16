package com.oyts.planplatform.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import com.oyts.planplatform.config.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshCookieService cookies;
    private final SecurityProperties security;
    @org.springframework.beans.factory.annotation.Value("${app.version}") private String serverVersion;

    public AuthController(AuthService authService, RefreshCookieService cookies, SecurityProperties security) {
        this.authService = authService; this.cookies = cookies; this.security = security;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletResponse response) {
        return respond(authService.register(request), response);
    }

    @PostMapping("/login")
    AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest servletRequest,
                                 HttpServletResponse response) {
        return respond(authService.login(request, servletRequest.getRemoteAddr()), response);
    }

    @PostMapping("/refresh")
    AuthDtos.TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return respond(authService.refresh(cookies.read(request)), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookies.read(request)); cookies.clear(response);
    }

    @GetMapping("/config")
    AuthDtos.AuthConfigResponse config() { return new AuthDtos.AuthConfigResponse(security.registrationEnabled(), security.passwordResetEnabled(), security.invitationEnabled(), security.emailVerificationEnabled(), true, true, serverVersion, 1); }

    @GetMapping("/me")
    AuthDtos.UserResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return authService.me(jwt.getSubject());
    }

    @PatchMapping("/me")
    AuthDtos.UserResponse updateProfile(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody AuthDtos.ProfileRequest request) {
        return authService.updateProfile(jwt.getSubject(), request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody AuthDtos.ChangePasswordRequest request, HttpServletResponse response) {
        authService.changePassword(jwt.getSubject(), request); cookies.clear(response);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void requestPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request, HttpServletRequest servletRequest) { authService.requestPasswordReset(request,servletRequest.getRemoteAddr()); }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetConfirmRequest request) { authService.confirmPasswordReset(request); }

    @PostMapping("/email-verification/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void requestEmailVerification(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) { authService.requestEmailVerification(jwt.getSubject()); }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmEmailVerification(@Valid @RequestBody AuthDtos.EmailVerificationConfirmRequest request) { authService.confirmEmailVerification(request); }

    @GetMapping("/sessions")
    List<AuthDtos.SessionResponse> sessions(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest request) {
        return authService.sessions(jwt.getSubject(), cookies.read(request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
                       @PathVariable String sessionId, HttpServletRequest request, HttpServletResponse response) {
        if (authService.revokeSession(jwt.getSubject(), sessionId, cookies.read(request))) cookies.clear(response);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        authService.logoutAll(jwt.getSubject()); cookies.clear(response);
    }

    private AuthDtos.TokenResponse respond(AuthService.IssuedTokens issued, HttpServletResponse response) {
        cookies.write(response, issued.refreshToken()); return issued.response();
    }
}
