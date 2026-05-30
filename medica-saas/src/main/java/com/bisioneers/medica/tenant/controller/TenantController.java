package com.bisioneers.medica.tenant.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.dto.TenantManagementDtos.*;
import com.bisioneers.medica.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**

- REST controller para gestión del perfil del tenant.
- 
- Endpoints:
- GET    /api/tenant              → Ver perfil del tenant actual
- PUT    /api/tenant              → Actualizar datos de contacto (ADMIN)
- PUT    /api/tenant/settings     → Actualizar settings JSON (ADMIN)
- 
- NOTA: El tenant siempre es el del usuario autenticado.
- No se puede ver ni editar tenants ajenos.
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantController {

	private final TenantService tenantService;

	public TenantController(TenantService tenantService) {
		this.tenantService = tenantService;
	}

	/**
  - Ver perfil del tenant actual.
  - Cualquier staff puede ver los datos del tenant.
	 */
	@GetMapping
	public ResponseEntity<TenantResponse> getMyTenant(
			@AuthenticationPrincipal StaffUserPrincipal principal
			) {
		TenantEntity tenant = tenantService.getById(principal.getTenantId());
		return ResponseEntity.ok(toResponse(tenant));
	}

	/**
  - Actualizar datos de contacto del tenant. Solo ADMIN.
  - Soporta actualización parcial (solo enviar campos a cambiar).
	 */
	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TenantResponse> updateProfile(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody UpdateTenantRequest request
			) {
		TenantEntity updated = tenantService.updateProfile(principal.getTenantId(), request);
		return ResponseEntity.ok(toResponse(updated));
	}

	/**
  - Actualizar settings del tenant (businessHours, etc.). Solo ADMIN.
  - 
  - Body: { “settings”: “{"businessHours":{…}}” }
  - 
  - El settings se almacena como JSON string en TenantEntity.settings.
  - Se valida que sea JSON válido antes de guardar.
  - 
  - Ejemplo completo de settings:
  - {
  - “settings”: “{"businessHours":{"MONDAY":{"open":"08:00","close":"18:00"},"TUESDAY":{"open":"08:00","close":"18:00"}}}”
  - }
	 */
	@PutMapping("/settings")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TenantResponse> updateSettings(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody UpdateTenantSettingsRequest request
			) {
		TenantEntity updated = tenantService.updateSettings(
				principal.getTenantId(), request.settings());
		return ResponseEntity.ok(toResponse(updated));
	}

	// ─── Mapper ───────────────────────────────────────────────────────

	private TenantResponse toResponse(TenantEntity e) {
		return new TenantResponse(
				e.getId(),
				e.getAlias(), 
				e.getDisplayName(),
				e.getContactEmail(),
				e.getContactPhone(),
				e.getAddress(),
				e.getTimezone(),
				e.isActive(),
				e.getSettings(),
				e.getSignatureLinkHours()
				);
	}
}