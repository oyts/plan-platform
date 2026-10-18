package com.oyts.planplatform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 10, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码至少包含一个英文字母和一个数字") String password,
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 300) String invitationToken
    ) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record UserResponse(String id, String email, String displayName, String role, boolean emailVerified) {}
    public record TokenResponse(String accessToken, long expiresInSeconds, UserResponse user) {}
    public record AuthConfigResponse(boolean registrationEnabled, boolean passwordResetEnabled, boolean invitationEnabled, boolean emailVerificationEnabled, boolean planRestDaysEnabled, boolean todoEnabled, String serverVersion, int apiVersion, boolean operationsAvailable) {}
    public record SessionResponse(String id, Instant createdAt, Instant expiresAt, boolean current) {}
    public record ProfileRequest(@NotBlank @Size(max = 80) String displayName) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
        @NotBlank @Size(min = 10, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码至少包含一个英文字母和一个数字") String newPassword) {}
    public record PasswordResetRequest(@Email @NotBlank String email) {}
    public record PasswordResetConfirmRequest(@Email @NotBlank String email, @NotBlank @Size(max = 300) String token,
        @NotBlank @Size(min = 10, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码至少包含一个英文字母和一个数字") String newPassword) {}
    public record InvitationRequest(@Email @NotBlank String email, Integer expiresInHours) {}
    public record InvitationResponse(String id, String email, Instant expiresAt, Instant acceptedAt, Instant revokedAt, String createdBy, String registrationUrl) {}
    public record EmailVerificationRequest() {}
    public record EmailVerificationConfirmRequest(@NotBlank @Size(max = 300) String token) {}
}
