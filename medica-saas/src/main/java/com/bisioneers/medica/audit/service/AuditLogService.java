package com.bisioneers.medica.audit.service;

import com.bisioneers.medica.audit.domain.AuditLogEntity;
import org.springframework.beans.factory.annotation.Value;
import com.bisioneers.medica.audit.domain.AuditLogRepository;
import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

	private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

	private final AuditLogRepository repository;
	private final UUID systemTenantId;

	public AuditLogService( AuditLogRepository repository,
			@Value("${app.audit.system-tenant-id}") UUID systemTenantId) {
		this.repository = repository;
		this.systemTenantId = systemTenantId;
	}

	// ─── Record Methods ────────────────────────────────

	/**
	 * Registra una acción de auditoría.
	 * Extrae automáticamente el usuario y la IP del contexto de seguridad.
	 */
	public void record(String action, String entityType, UUID entityId,
			String entityName, String details) {
		try {
			AuditLogEntity entry = new AuditLogEntity();
			entry.setId(UUID.randomUUID());
			entry.setAction(action);
			entry.setEntityType(entityType);
			entry.setEntityId(entityId);
			entry.setEntityName(entityName);
			entry.setDetails(details);
			entry.setTimestamp(Instant.now());

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth != null && auth.getPrincipal() instanceof StaffUserPrincipal principal) {
				entry.setTenantId(principal.getTenantId());
				entry.setUserId(principal.getUserId());
				entry.setUserEmail(principal.getUsername());
			} else {
				// Operación de sistema (jobs, webhooks): sin principal.
				// Asignamos el tenant SYSTEM para no violar el NOT NULL
				// y conservar el rastro de auditoría.
				entry.setTenantId(systemTenantId);
				entry.setUserEmail("system");
			}

			entry.setIpAddress(extractClientIp());

			repository.save(entry);
			log.debug("Audit: {} {} {} by {}", action, entityType, entityId, entry.getUserEmail());
		} catch (Exception e) {
			log.error("Failed to record audit log: {}", e.getMessage());
		}
	}

	/** Shortcut sin details */
	public void record(String action, String entityType, UUID entityId, String entityName) {
		record(action, entityType, entityId, entityName, null);
	}

	/** Para acciones sin entidad (LOGIN, LOGOUT) */
	public void recordSystemAction(UUID tenantId, UUID userId, String userEmail,
			String action, String details) {
		try {
			AuditLogEntity entry = new AuditLogEntity();
			entry.setId(UUID.randomUUID());
			entry.setTenantId(tenantId);
			entry.setUserId(userId);
			entry.setUserEmail(userEmail);
			entry.setAction(action);
			entry.setEntityType("SYSTEM");
			entry.setDetails(details);
			entry.setIpAddress(extractClientIp());
			entry.setTimestamp(Instant.now());

			repository.save(entry);
		} catch (Exception e) {
			log.error("Failed to record system audit: {}", e.getMessage());
		}
	}

	// ─── Query Methods ─────────────────────────────────

	/** Listar logs con filtros opcionales */
	public Page<AuditLogEntity> getLogs(UUID tenantId, String action, String entityType,
			UUID userId, Instant startDate, Instant endDate,
			int page, int size) {
		return repository.findFiltered(
				tenantId, action, entityType, userId, startDate, endDate,
				PageRequest.of(page, size));
	}

	/** Historial de una entidad específica */
	public List<AuditLogEntity> getEntityHistory(UUID tenantId, String entityType, UUID entityId) {
		return repository.findByTenantIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
				tenantId, entityType, entityId);
	}

	/** Resumen de acciones (últimos 30 días) para dashboard */
	public Map<String, Long> getActionSummary(UUID tenantId) {
		Instant since = Instant.now().minus(java.time.Duration.ofDays(30));
		List<Object[]> results = repository.countByActionSince(tenantId, since);
		return results.stream().collect(
				Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
	}

	// ─── Helpers ───────────────────────────────────────

	private String extractClientIp() {
		try {
			ServletRequestAttributes attrs =
					(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			if (attrs != null) {
				HttpServletRequest request = attrs.getRequest();
				String xff = request.getHeader("X-Forwarded-For");
				if (xff != null && !xff.isEmpty()) {
					return xff.split(",")[0].trim();
				}
				return request.getRemoteAddr();
			}
		} catch (Exception ignored) {}
		return null;
	}
}

