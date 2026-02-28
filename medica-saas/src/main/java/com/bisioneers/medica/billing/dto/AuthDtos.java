package com.bisioneers.medica.billing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs para los endpoints de autenticación.
 * Agrupados en una clase contenedora para mantener el paquete limpio.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // ─── Login ────────────────────────────────────────────────────────

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {}

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType
    ) {
        public LoginResponse(String accessToken, String refreshToken, long expiresInSeconds) {
            this(accessToken, refreshToken, expiresInSeconds, "Bearer");
        }
    }

    // ─── Refresh Token ────────────────────────────────────────────────

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record RefreshResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType
    ) {
        public RefreshResponse(String accessToken, String refreshToken, long expiresInSeconds) {
            this(accessToken, refreshToken, expiresInSeconds, "Bearer");
        }
    }

    // ─── Cambio de Contraseña ─────────────────────────────────────────

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, message = "La contraseña debe tener mínimo 8 caracteres")
            String newPassword
    ) {}

    // ─── Registro / Onboarding (Tenant + Admin User) ─────────────────
    //
    // NOTA: StaffUserEntity actualmente solo tiene email, passwordHash, role,
    // tenantId, enabled. No tiene firstName/lastName/phone/specialty.
    // Cuando se agreguen esos campos a la entidad, se pueden descomentar aquí.

    public record RegisterTenantRequest(
            @NotBlank @Size(min = 2, max = 100)
            String tenantName,

            @NotBlank @Size(min = 3, max = 50)
            String tenantAlias,

            @NotBlank @Email
            String adminEmail,

            @NotBlank @Size(min = 8)
            String adminPassword
    ) {}

    public record RegisterTenantResponse(
            String tenantId,
            String userId,
            String accessToken,
            String refreshToken,
            String message
    ) {}
}
