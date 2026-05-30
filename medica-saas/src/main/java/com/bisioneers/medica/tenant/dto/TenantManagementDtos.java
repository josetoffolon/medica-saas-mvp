package com.bisioneers.medica.tenant.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

/**

- DTOs para gestión de Tenant y Staff.
 */
public final class TenantManagementDtos {

	private TenantManagementDtos() {}

	// ═══════════════════════════════════════════════════════════════════
	// TENANT DTOs
	// ═══════════════════════════════════════════════════════════════════

	public record UpdateTenantRequest(
			@Size(max = 100)
			String displayName,
			@Email @Size(max = 160)
			String contactEmail,

			@Size(max = 20)
			String contactPhone,

			@Size(max = 500)
			String address,

			@Size(max = 50)
			String timezone,
			
			/**
             * Duración del link de firma remota en horas.
             * Rango: 1 a 168 horas (1 semana).
             * Si llega null, se mantiene el valor actual del tenant.
             */
            @Min(value = 1, message = "Mínimo 1 hora")
            @Max(value = 168, message = "Máximo 168 horas (1 semana)")
            Integer signatureLinkHours
			) {}

	/**
  - Para actualizar settings (businessHours, etc.) como JSON.
  - Se recibe como String y se guarda directamente en TenantEntity.settings.
	 */
	public record UpdateTenantSettingsRequest(
			@NotBlank
			String settings
			) {}

	public record TenantResponse(
			UUID id,
			String alias,
			String displayName,
			String contactEmail,
			String contactPhone,
			String address,
			String timezone,
			boolean active,
			String settings,
			@Min(1) @Max(168)
			Integer signatureLinkHours
			) {}

	// ═══════════════════════════════════════════════════════════════════
	// STAFF DTOs
	// ═══════════════════════════════════════════════════════════════════

	public record CreateStaffRequest(
			@NotBlank @Email @Size(max = 160)
			String email,
			@NotBlank @Size(min = 8)
			String password,

			@NotBlank @Size(max = 20)
			String role
			) {}

	public record UpdateStaffRequest(
			@Email @Size(max = 160)
			String email,
			@Size(max = 20)
			String role

			) {}

	public record ResetPasswordRequest(
			@NotBlank @Size(min = 8)
			String newPassword
			) {}

	public record StaffResponse(
			UUID id,
			String email,
			String role,
			boolean enabled
			) {}
}