package com.bisioneers.medica.tenant.controller;

import com.bisioneers.medica.billing.security.StaffUserEntity;
import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.tenant.dto.TenantManagementDtos.*;
import com.bisioneers.medica.tenant.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**

- REST controller para gestión de usuarios del staff del tenant.
- 
- Endpoints (todos requieren rol ADMIN):
- GET    /api/staff                    → Listar usuarios del tenant
- GET    /api/staff/{id}               → Detalle de un usuario
- POST   /api/staff                    → Crear usuario
- PUT    /api/staff/{id}               → Actualizar email/rol
- PATCH  /api/staff/{id}/reset-password → Resetear contraseña
- PATCH  /api/staff/{id}/deactivate    → Desactivar usuario
- PATCH  /api/staff/{id}/reactivate    → Reactivar usuario
- 
- Roles válidos: ADMIN, MEDICO, RECEPCION, ASISTENTE
- 
- Protección: No se puede desactivar al último ADMIN del tenant.
 */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

	private final StaffService staffService;

	public StaffController(StaffService staffService) {
		this.staffService = staffService;
	}

	// ─── LIST ─────────────────────────────────────────────────────────

	@GetMapping
	public ResponseEntity<List<StaffResponse>> list(
			@AuthenticationPrincipal StaffUserPrincipal principal
			) {
		List<StaffResponse> staff = staffService
				.listByTenant(principal.getTenantId())
				.stream()
				.map(this::toResponse)
				.toList();

		return ResponseEntity.ok(staff);

	}

	// ─── GET ──────────────────────────────────────────────────────────

	@GetMapping("/{id}")
	public ResponseEntity<StaffResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		StaffUserEntity user = staffService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(user));
	}

	// ─── CREATE ───────────────────────────────────────────────────────

	@PostMapping
	public ResponseEntity<StaffResponse> create(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody CreateStaffRequest request
			) {
		StaffUserEntity created = staffService.create(principal.getTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
	}

	// ─── UPDATE ───────────────────────────────────────────────────────

	@PutMapping("/{id}")
	public ResponseEntity<StaffResponse> update(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateStaffRequest request
			) {
		StaffUserEntity updated = staffService.update(principal.getTenantId(), id, request);
		return ResponseEntity.ok(toResponse(updated));
	}

	// ─── RESET PASSWORD ───────────────────────────────────────────────

	@PatchMapping("/{id}/reset-password")
	public ResponseEntity<Map<String, String>> resetPassword(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody ResetPasswordRequest request
			) {
		staffService.resetPassword(principal.getTenantId(), id, request.newPassword());
		return ResponseEntity.ok(Map.of("message", "Contraseña actualizada"));
	}

	// ─── DEACTIVATE / REACTIVATE ──────────────────────────────────────

	@PatchMapping("/{id}/deactivate")
	public ResponseEntity<Map<String, String>> deactivate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		staffService.deactivate(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Usuario desactivado"));
	}

	@PatchMapping("/{id}/reactivate")
	public ResponseEntity<Map<String, String>> reactivate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		staffService.reactivate(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Usuario reactivado"));
	}

	// ─── Mapper ───────────────────────────────────────────────────────

	private StaffResponse toResponse(StaffUserEntity e) {
		return new StaffResponse(
				e.getId(), e.getEmail(), e.getRole(), e.isEnabled()
				);
	}
}