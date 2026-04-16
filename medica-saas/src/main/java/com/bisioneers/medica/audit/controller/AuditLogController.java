package com.bisioneers.medica.audit.controller;

import com.bisioneers.medica.audit.domain.AuditLogEntity;
import com.bisioneers.medica.audit.service.AuditLogService;
import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API de auditoría — solo accesible por ADMIN.
 *
 * GET /api/audit             → Listar logs con filtros
 * GET /api/audit/entity/{type}/{id}  → Historial de una entidad
 * GET /api/audit/summary     → Resumen de acciones (últimos 30 días)
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

	private final AuditLogService auditLogService;

	public AuditLogController(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	/**
	 * Listar logs de auditoría con filtros opcionales.
	 *
	 * Params:
	 *   page, size      — paginación (default 0, 50)
	 *   action          — filtrar por acción (CREATE, UPDATE, DELETE, SIGN, etc.)
	 *   entityType      — filtrar por tipo (PATIENT, APPOINTMENT, etc.)
	 *   userId          — filtrar por usuario que realizó la acción
	 *   startDate       — fecha inicio (ISO instant)
	 *   endDate         — fecha fin (ISO instant)
	 */
	@GetMapping
	public ResponseEntity<Page<AuditLogDto>> getLogs(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) UUID userId,
			@RequestParam(required = false) Instant startDate,
			@RequestParam(required = false) Instant endDate
			) {
		Page<AuditLogEntity> logs = auditLogService.getLogs(
				principal.getTenantId(), action, entityType, userId,
				startDate, endDate, page, Math.min(size, 100));

		Page<AuditLogDto> dtos = logs.map(AuditLogDto::from);
		return ResponseEntity.ok(dtos);
	}

	/**
	 * Historial de una entidad específica.
	 * Ej: GET /api/audit/entity/PATIENT/550e8400-e29b-41d4-a716-446655440000
	 */
	@GetMapping("/entity/{entityType}/{entityId}")
	public ResponseEntity<List<AuditLogDto>> getEntityHistory(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable String entityType,
			@PathVariable UUID entityId
			) {
		List<AuditLogEntity> history = auditLogService.getEntityHistory(
				principal.getTenantId(), entityType.toUpperCase(), entityId);

		List<AuditLogDto> dtos = history.stream().map(AuditLogDto::from).toList();
		return ResponseEntity.ok(dtos);
	}

	/**
	 * Resumen de acciones (últimos 30 días).
	 * Retorna: { "CREATE": 45, "UPDATE": 30, "SIGN": 12, ... }
	 */
	@GetMapping("/summary")
	public ResponseEntity<Map<String, Long>> getSummary(
			@AuthenticationPrincipal StaffUserPrincipal principal
			) {
		return ResponseEntity.ok(auditLogService.getActionSummary(principal.getTenantId()));
	}

	// ─── DTO ──────────────────────────────────────────

	public record AuditLogDto(
			UUID id,
			UUID userId,
			String userEmail,
			String action,
			String entityType,
			UUID entityId,
			String entityName,
			String details,
			String ipAddress,
			Instant timestamp
			) {
		public static AuditLogDto from(AuditLogEntity e) {
			return new AuditLogDto(
					e.getId(), e.getUserId(), e.getUserEmail(),
					e.getAction(), e.getEntityType(), e.getEntityId(),
					e.getEntityName(), e.getDetails(), e.getIpAddress(),
					e.getTimestamp()
					);
		}
	}
}

