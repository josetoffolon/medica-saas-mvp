package com.bisioneers.medica.tenant.service;

import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import com.bisioneers.medica.tenant.dto.TenantManagementDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**

- Servicio de negocio para gestión del perfil del tenant.
- 
- Permite al ADMIN del tenant:
- - Ver y actualizar datos de contacto (displayName, email, phone, address, timezone)
- - Configurar settings (businessHours, etc.) como JSON
- 
- NOTA: El registro/onboarding de tenant se hace en AuthController (POST /api/auth/register).
- Este service solo gestiona el tenant YA existente.
 */
@Service
public class TenantService {

	private static final Logger log = LoggerFactory.getLogger(TenantService.class);

	private final TenantRepository tenantRepository;
	private final ObjectMapper objectMapper;

	public TenantService(TenantRepository tenantRepository, ObjectMapper objectMapper) {
		this.tenantRepository = tenantRepository;
		this.objectMapper = objectMapper;
	}

	// ─── READ ─────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public TenantEntity getById(UUID tenantId) {
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
	}

	// ─── UPDATE PROFILE ───────────────────────────────────────────────

	@Transactional
	public TenantEntity updateProfile(UUID tenantId, UpdateTenantRequest request) {
		TenantEntity tenant = getById(tenantId);

		if (request.displayName() != null) tenant.setDisplayName(request.displayName());
		if (request.contactEmail() != null) tenant.setContactEmail(request.contactEmail());
		if (request.contactPhone() != null) tenant.setContactPhone(request.contactPhone());
		if (request.address() != null) tenant.setAddress(request.address());
		if (request.timezone() != null) tenant.setTimezone(request.timezone());

		return tenantRepository.save(tenant);

	}

	// ─── UPDATE SETTINGS ──────────────────────────────────────────────

	/**
  - Actualiza el JSON de settings del tenant.
  - Valida que sea JSON válido antes de guardar.
  - 
  - Settings incluye businessHours y cualquier configuración
  - específica del tenant.
	 */
	@Transactional
	public TenantEntity updateSettings(UUID tenantId, String settingsJson) {
		// Validar que sea JSON válido
		try {
			objectMapper.readTree(settingsJson);
		} catch (Exception e) {
			throw new IllegalArgumentException("El settings no es JSON válido: " + e.getMessage());
		}

		TenantEntity tenant = getById(tenantId);
		tenant.setSettings(settingsJson);

		log.info("Settings updated for tenant {}", tenantId);
		return tenantRepository.save(tenant);
	}
}