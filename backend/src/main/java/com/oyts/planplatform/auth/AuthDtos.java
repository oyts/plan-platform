package com.oyts.planplatform.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String displayName
    ) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record UserResponse(String id, String email, String displayName, String role) {}
    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {}
}
