package com.bisioneers.medica.documents.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud de firma para un PatientDocument.
 *
 * Para flujo IN_PERSON: token no se usa (firma desde la sesión del staff).
 * Para flujo REMOTE: token JWT corto enviado al paciente.
 *
 * Seguridad:
 *  - tokenHash almacena solo SHA-256 del token, no el token en claro
 *    (si la BD se compromete, no se pueden generar firmas falsas)
 *  - El token completo solo se devuelve UNA vez al crear la solicitud
 *  - patientDocumentNumber se valida cuando el paciente abre el link
 *
 * Inmutabilidad:
 *  - Status PENDING es el único editable (a SIGNED/EXPIRED/CANCELLED)
 *  - Una vez en estado terminal, nadie puede modificar
 */
@Entity
@Table(name = "signature_request",
indexes = {
		@Index(name = "idx_sigreq_tenant_doc", columnList = "tenant_id,patient_document_id"),
		@Index(name = "idx_sigreq_tenant_status", columnList = "tenant_id,status"),
		@Index(name = "idx_sigreq_token_hash", columnList = "token_hash"),
		@Index(name = "idx_sigreq_expires_at", columnList = "expires_at")
})
public class SignatureRequestEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "patient_document_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID patientDocumentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SignatureMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SignatureRequestStatus status = SignatureRequestStatus.PENDING;

	/**
	 * SHA-256 hex del token. NUNCA almacenar el token en claro.
	 * Solo para flujo REMOTE; en IN_PERSON queda null.
	 */
	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_channel", length = 20)
	private RemoteDeliveryChannel deliveryChannel;

	/** Destinatario al que se intentó enviar (email/teléfono). Audit, opcional. */
	@Column(name = "delivery_target", length = 200)
	private String deliveryTarget;

	/** True si la entrega via WhatsApp/Email fue exitosa */
	@Column(name = "delivery_successful")
	private Boolean deliverySuccessful;

	@Column(name = "delivery_error", length = 500)
	private String deliveryError;

	/** Cuántos intentos de firma fallida (cédula incorrecta) */
	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts = 0;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "signed_at")
	private Instant signedAt;

	/** Staff que inició la solicitud (creador del flujo de firma) */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "created_by_staff_user_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID createdByStaffUserId;

	/**
	 * Staff que acompañó físicamente la firma (puede ser distinto al creator).
	 * En flujo IN_PERSON, normalmente coincide con el authenticated principal
	 * que ejecuta el endpoint de confirmación. En REMOTE queda null.
	 */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "witness_staff_user_id", columnDefinition = "BINARY(16)")
	private UUID witnessStaffUserId;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
	}

	// ─── Getters / Setters ──────────────────────────────────────

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public UUID getPatientDocumentId() { return patientDocumentId; }
	public void setPatientDocumentId(UUID id) { this.patientDocumentId = id; }

	public SignatureMethod getMethod() { return method; }
	public void setMethod(SignatureMethod m) { this.method = m; }

	public SignatureRequestStatus getStatus() { return status; }
	public void setStatus(SignatureRequestStatus s) { this.status = s; }

	public String getTokenHash() { return tokenHash; }
	public void setTokenHash(String h) { this.tokenHash = h; }

	public RemoteDeliveryChannel getDeliveryChannel() { return deliveryChannel; }
	public void setDeliveryChannel(RemoteDeliveryChannel c) { this.deliveryChannel = c; }

	public String getDeliveryTarget() { return deliveryTarget; }
	public void setDeliveryTarget(String t) { this.deliveryTarget = t; }

	public Boolean getDeliverySuccessful() { return deliverySuccessful; }
	public void setDeliverySuccessful(Boolean s) { this.deliverySuccessful = s; }

	public String getDeliveryError() { return deliveryError; }
	public void setDeliveryError(String e) { this.deliveryError = e; }

	public int getFailedAttempts() { return failedAttempts; }
	public void setFailedAttempts(int n) { this.failedAttempts = n; }

	public Instant getExpiresAt() { return expiresAt; }
	public void setExpiresAt(Instant at) { this.expiresAt = at; }

	public Instant getSignedAt() { return signedAt; }
	public void setSignedAt(Instant at) { this.signedAt = at; }

	public UUID getCreatedByStaffUserId() { return createdByStaffUserId; }
	public void setCreatedByStaffUserId(UUID id) { this.createdByStaffUserId = id; }

	public UUID getWitnessStaffUserId() { return witnessStaffUserId; }
	public void setWitnessStaffUserId(UUID id) { this.witnessStaffUserId = id; }
}
