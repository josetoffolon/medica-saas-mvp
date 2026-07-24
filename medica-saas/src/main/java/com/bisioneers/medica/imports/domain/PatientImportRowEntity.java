package com.bisioneers.medica.imports.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Una fila del CSV, persistida para preview/reintento/auditoría.
 *
 * rawData        = JSON de la fila tal como llegó (columnas canónicas)
 * normalizedData = JSON de los valores ya normalizados (teléfono E.164, etc.)
 * messages       = JSON array de avisos/errores legibles para el frontend
 */
@Entity
@Table(name = "patient_import_row",
indexes = {
		@Index(name = "idx_import_row_batch", columnList = "batch_id,status")
})
public class PatientImportRowEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "batch_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID batchId;

	/** Número de fila en el archivo (1-based, sin contar encabezado). */
	@Column(name = "row_number", nullable = false)
	private int rowNumber;

	@Lob
	@Column(name = "raw_data", nullable = false, columnDefinition = "TEXT")
	private String rawData;

	@Lob
	@Column(name = "normalized_data", columnDefinition = "TEXT")
	private String normalizedData;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImportRowStatus status;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String messages;

	/** Si es DUPLICATE, el paciente existente con el que colisiona. */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "match_patient_id", columnDefinition = "BINARY(16)")
	private UUID matchPatientId;

	/** Por qué se consideró duplicado: DOCUMENT, EMAIL, LEGACY_ID, IN_FILE. */
	@Column(name = "match_reason", length = 40)
	private String matchReason;

	/** Paciente creado a partir de esta fila (post-commit). */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "patient_id", columnDefinition = "BINARY(16)")
	private UUID patientId;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
	}

	// ─── Getters / Setters ────────────────────────────────────────────

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public UUID getBatchId() { return batchId; }
	public void setBatchId(UUID batchId) { this.batchId = batchId; }

	public int getRowNumber() { return rowNumber; }
	public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

	public String getRawData() { return rawData; }
	public void setRawData(String rawData) { this.rawData = rawData; }

	public String getNormalizedData() { return normalizedData; }
	public void setNormalizedData(String normalizedData) { this.normalizedData = normalizedData; }

	public ImportRowStatus getStatus() { return status; }
	public void setStatus(ImportRowStatus status) { this.status = status; }

	public String getMessages() { return messages; }
	public void setMessages(String messages) { this.messages = messages; }

	public UUID getMatchPatientId() { return matchPatientId; }
	public void setMatchPatientId(UUID matchPatientId) { this.matchPatientId = matchPatientId; }

	public String getMatchReason() { return matchReason; }
	public void setMatchReason(String matchReason) { this.matchReason = matchReason; }

	public UUID getPatientId() { return patientId; }
	public void setPatientId(UUID patientId) { this.patientId = patientId; }
}