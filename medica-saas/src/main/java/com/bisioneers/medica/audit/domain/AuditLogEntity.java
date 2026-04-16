package com.bisioneers.medica.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de auditoría para rastrear todas las acciones importantes
 * del sistema: quién hizo qué, cuándo, y sobre qué entidad.
 *
 * Cada tenant solo ve sus propios logs (filtrado por tenantId).
 * Los logs son inmutables — solo se insertan, nunca se editan o eliminan.
 */
@Entity
@Table(name = "audit_log", indexes = {
		@Index(name = "idx_audit_tenant_timestamp", columnList = "tenant_id, timestamp DESC"),
		@Index(name = "idx_audit_entity", columnList = "tenant_id, entity_type, entity_id"),
		@Index(name = "idx_audit_user", columnList = "tenant_id, user_id"),
		@Index(name = "idx_audit_action", columnList = "tenant_id, action")
})
public class AuditLogEntity {

	@Id
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(name = "tenant_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID tenantId;

	/** ID del usuario que realizó la acción */
	@Column(name = "user_id", columnDefinition = "BINARY(16)")
	private UUID userId;

	/** Email del usuario (desnormalizado para consultas rápidas sin JOIN) */
	@Column(name = "user_email", length = 255)
	private String userEmail;

	/** Tipo de acción: CREATE, UPDATE, DELETE, SIGN, UNSIGN, LOGIN, LOGOUT, etc. */
	@Column(name = "action", nullable = false, length = 50)
	private String action;

	/** Tipo de entidad afectada: PATIENT, APPOINTMENT, MEDICAL_RECORD, SERVICE, STAFF, TENANT */
	@Column(name = "entity_type", nullable = false, length = 50)
	private String entityType;

	/** ID de la entidad afectada (puede ser null para acciones globales como LOGIN) */
	@Column(name = "entity_id", columnDefinition = "BINARY(16)")
	private UUID entityId;

	/** Nombre/descripción legible de la entidad (ej: "María García", "Botox Frontal") */
	@Column(name = "entity_name", length = 255)
	private String entityName;

	/** Detalles adicionales en JSON (cambios antes/después, razón, etc.) */
	@Column(name = "details", columnDefinition = "TEXT")
	private String details;

	/** IP del cliente que realizó la acción */
	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	/** Momento exacto de la acción */
	@Column(name = "timestamp", nullable = false)
	private Instant timestamp;

	// ─── Constructors ─────────────────────────────────

	public AuditLogEntity() {}

	// ─── Getters/Setters ──────────────────────────────

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

	public UUID getUserId() { return userId; }
	public void setUserId(UUID userId) { this.userId = userId; }

	public String getUserEmail() { return userEmail; }
	public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

	public String getAction() { return action; }
	public void setAction(String action) { this.action = action; }

	public String getEntityType() { return entityType; }
	public void setEntityType(String entityType) { this.entityType = entityType; }

	public UUID getEntityId() { return entityId; }
	public void setEntityId(UUID entityId) { this.entityId = entityId; }

	public String getEntityName() { return entityName; }
	public void setEntityName(String entityName) { this.entityName = entityName; }

	public String getDetails() { return details; }
	public void setDetails(String details) { this.details = details; }

	public String getIpAddress() { return ipAddress; }
	public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

	public Instant getTimestamp() { return timestamp; }
	public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}

