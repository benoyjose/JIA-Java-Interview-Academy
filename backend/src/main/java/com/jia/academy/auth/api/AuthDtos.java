package com.jia.academy.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank @Size(max=160) String name,
                                  @NotBlank @Email @Size(max=254) String email,
                                  @NotBlank @Size(min=12,max=128) String password) {}
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email,
                               @NotBlank @Size(max=128) String password,
                               @Size(max=100) String deviceName) {}
    public record UserResponse(UUID id, String name, String email, List<String> roles) {}
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, Instant expiresAt, UserResponse user) {}
    public record RefreshRequest(@NotBlank @Size(max=160) String refreshToken) {}
    public record PasswordResetRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record PasswordResetConfirmRequest(@NotBlank @Size(max=180) String token,
                                              @NotBlank @Size(min=12,max=128) String newPassword) {}
}
