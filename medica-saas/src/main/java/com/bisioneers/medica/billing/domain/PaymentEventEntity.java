package com.bisioneers.medica.billing.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento crudo recibido de Paguelo Fácil para una transacción.
 *
 * #16: reemplaza la concatenación sin límite en PaymentTransaction.payloadJson.
 * Cada webhook / return / consulta de polling se guarda como UNA fila,
 * conservando el historial completo y consultable (útil para conciliación
 * contable y auditoría), sin que la transacción crezca sin control.
 *
 * Inmutable: solo se inserta, nunca se edita ni borra.
 */
@Entity
@Table(name = "payment_event",
indexes = {
		@Index(name = "idx_pevent_tenant_tx", columnList = "tenant_id,transaction_id,created_at"),
		@Index(name = "idx_pevent_tenant_created", columnList = "tenant_id,created_at")
})
public class PaymentEventEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	/** Transacción a la que pertenece el evento. */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "transaction_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID transactionId;

	/** Origen del evento: WEBHOOK | RETURN | POLL | CHECKOUT | ERROR */
	@Column(nullable = false, length = 20)
	private String source;

	/** Resultado derivado en el momento, si aplica: PAID | DECLINED | etc. (opcional) */
	@Column(length = 30)
	private String outcome;

	/** Payload crudo recibido de PF (o JSON de error/auditoría que generamos). */
	@Lob
	@Column(name = "raw_json", columnDefinition = "LONGTEXT")
	private String rawJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
		if (createdAt == null) createdAt = Instant.now();
	}

	public PaymentEventEntity() {}

	public PaymentEventEntity(UUID tenantId, UUID transactionId, String source,
			String outcome, String rawJson) {
		setTenantId(tenantId);
		this.transactionId = transactionId;
		this.source = source;
		this.outcome = outcome;
		this.rawJson = rawJson;
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getTransactionId() { return transactionId; }
	public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public String getOutcome() { return outcome; }
	public void setOutcome(String outcome) { this.outcome = outcome; }
	public String getRawJson() { return rawJson; }
	public void setRawJson(String rawJson) { this.rawJson = rawJson; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}