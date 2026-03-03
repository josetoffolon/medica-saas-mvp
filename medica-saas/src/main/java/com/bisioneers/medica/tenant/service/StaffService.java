package com.bisioneers.medica.tenant.service;

import com.bisioneers.medica.billing.security.StaffUserEntity;
import com.bisioneers.medica.billing.security.StaffUserRepository;
import com.bisioneers.medica.tenant.dto.TenantManagementDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**

- Servicio de negocio para gestión de staff dentro de un tenant.
- 
- Permite al ADMIN:
- - Listar usuarios del tenant
- - Crear nuevos usuarios (MEDICO, RECEPCION, ASISTENTE)
- - Editar email/rol de un usuario
- - Resetear contraseña de un usuario
- - Desactivar/reactivar usuarios
- 
- NOTA: StaffUserEntity no extiende TenantScopedEntity (está en billing.security).
- Tiene tenantId como UUID directo. La validación de tenant se hace manualmente.
 */
@Service
public class StaffService {

	private static final Logger log = LoggerFactory.getLogger(StaffService.class);

	private static final Set<String> VALID_ROLES = Set.of(
			"ADMIN", "MEDICO", "RECEPCION", "ASISTENTE"
			);

	private final StaffUserRepository staffUserRepository;
	private final PasswordEncoder passwordEncoder;

	public StaffService(StaffUserRepository staffUserRepository,
			PasswordEncoder passwordEncoder) {
		this.staffUserRepository = staffUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	// ─── LIST ─────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<StaffUserEntity> listByTenant(UUID tenantId) {
		return staffUserRepository.findByTenantId(tenantId);
	}

	// ─── GET ──────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public StaffUserEntity getById(UUID tenantId, UUID staffId) {
		StaffUserEntity user = staffUserRepository.findById(staffId)
				.orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

		if (!tenantId.equals(user.getTenantId())) {
			throw new IllegalArgumentException("Acceso denegado");
		}

		return user;
	}

	// ─── CREATE ───────────────────────────────────────────────────────

	@Transactional
	public StaffUserEntity create(UUID tenantId, CreateStaffRequest request) {
		// Validar rol
		validateRole(request.role());

		// Validar email único
		if (staffUserRepository.findByEmail(request.email()).isPresent()) {
			throw new IllegalArgumentException(
					"Ya existe un usuario con email: " + request.email());
		}

		StaffUserEntity user = new StaffUserEntity();
		user.setId(UUID.randomUUID());
		user.setTenantId(tenantId);
		user.setEmail(request.email());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole(request.role());
		user.setEnabled(true);

		StaffUserEntity saved = staffUserRepository.save(user);
		log.info("Staff created: email={}, role={}, tenant={}", request.email(), request.role(), tenantId);

		return saved;

	}

	// ─── UPDATE ───────────────────────────────────────────────────────

	@Transactional
	public StaffUserEntity update(UUID tenantId, UUID staffId, UpdateStaffRequest request) {
		StaffUserEntity user = getById(tenantId, staffId);

		// Actualizar email si cambió
		if (request.email() != null && !request.email().equals(user.getEmail())) {
			// Verificar unicidad
			staffUserRepository.findByEmail(request.email()).ifPresent(existing -> {
				throw new IllegalArgumentException(
						"Ya existe un usuario con email: " + request.email());
			});
			user.setEmail(request.email());
		}

		// Actualizar rol si viene
		if (request.role() != null) {
			validateRole(request.role());
			user.setRole(request.role());
		}

		log.info("Staff updated: id={}, email={}, role={}", staffId, user.getEmail(), user.getRole());
		return staffUserRepository.save(user);

	}

	// ─── RESET PASSWORD ───────────────────────────────────────────────

	@Transactional
	public void resetPassword(UUID tenantId, UUID staffId, String newPassword) {
		StaffUserEntity user = getById(tenantId, staffId);
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		staffUserRepository.save(user);

		log.info("Password reset: staffId={}", staffId);

	}

	// ─── DEACTIVATE / REACTIVATE ──────────────────────────────────────

	@Transactional
	public void deactivate(UUID tenantId, UUID staffId) {
		StaffUserEntity user = getById(tenantId, staffId);

		// No permitir auto-desactivación del último ADMIN
		if ("ADMIN".equals(user.getRole())) {
			long adminCount = listByTenant(tenantId).stream()
					.filter(u -> "ADMIN".equals(u.getRole()) && u.isEnabled())
					.count();
			if (adminCount <= 1) {
				throw new IllegalStateException(
						"No se puede desactivar al último administrador del tenant");
			}
		}

		user.setEnabled(false);
		staffUserRepository.save(user);
		log.info("Staff deactivated: id={}, email={}", staffId, user.getEmail());

	}

	@Transactional
	public void reactivate(UUID tenantId, UUID staffId) {
		StaffUserEntity user = getById(tenantId, staffId);
		user.setEnabled(true);
		staffUserRepository.save(user);
		log.info("Staff reactivated: id={}, email={}", staffId, user.getEmail());
	}

	// ─── Helpers ──────────────────────────────────────────────────────

	private void validateRole(String role) {
		if (!VALID_ROLES.contains(role)) {
			throw new IllegalArgumentException(
					"Rol inválido: " + role + ". Roles válidos: " + VALID_ROLES);
		}
	}
}