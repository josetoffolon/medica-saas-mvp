package com.bisioneers.medica.imports.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Un lote de importación de pacientes desde un CSV.
 *
 * Ciclo de vida: ANALYZING → ANALYZED → COMMITTING → COMMITTED (→ REVERTED)
 * El análisis NO escribe pacientes; solo persiste el batch y sus filas
 * (PatientImportRowEntity) para permitir preview, reintento y auditoría.
 */
@Entity
@Table(name = "patient_import_batch",
indexes = {
		@Index(name = "idx_import_batch_tenant", columnList = "tenant_id,created_at")
},
uniqueConstraints = {
		@UniqueConstraint(name = "uk_import_batch_tenant_hash",
				columnNames = {"tenant_id", "file_hash"})
})
public class PatientImportBatchEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	/** SHA-256 del contenido: evita re-analizar el mismo archivo por accidente. */
	@Column(name = "file_hash", nullable = false, length = 64)
	private String fileHash;

	@Column(name = "file_size_bytes", nullable = false)
	private long fileSizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImportBatchStatus status = ImportBatchStatus.ANALYZING;

	@Column(name = "total_rows", nullable = false)
	private int totalRows = 0;

	@Column(name = "ok_rows", nullable = false)
	private int okRows = 0;

	@Column(name = "warning_rows", nullable = false)
	private int warningRows = 0;

	@Column(name = "error_rows", nullable = false)
	private int errorRows = 0;

	@Column(name = "duplicate_rows", nullable = false)
	private int duplicateRows = 0;

	@Column(name = "imported_rows", nullable = false)
	private int importedRows = 0;

	@Column(name = "skipped_rows", nullable = false)
	private int skippedRows = 0;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "committed_at")
	private Instant committedAt;

	@Column(name = "reverted_at")
	private Instant revertedAt;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
	}

	// ─── Getters / Setters ────────────────────────────────────────────

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }

	public String getFileHash() { return fileHash; }
	public void setFileHash(String fileHash) { this.fileHash = fileHash; }

	public long getFileSizeBytes() { return fileSizeBytes; }
	public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

	public ImportBatchStatus getStatus() { return status; }
	public void setStatus(ImportBatchStatus status) { this.status = status; }

	public int getTotalRows() { return totalRows; }
	public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

	public int getOkRows() { return okRows; }
	public void setOkRows(int okRows) { this.okRows = okRows; }

	public int getWarningRows() { return warningRows; }
	public void setWarningRows(int warningRows) { this.warningRows = warningRows; }

	public int getErrorRows() { return errorRows; }
	public void setErrorRows(int errorRows) { this.errorRows = errorRows; }

	public int getDuplicateRows() { return duplicateRows; }
	public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }

	public int getImportedRows() { return importedRows; }
	public void setImportedRows(int importedRows) { this.importedRows = importedRows; }

	public int getSkippedRows() { return skippedRows; }
	public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }

	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

	public Instant getCommittedAt() { return committedAt; }
	public void setCommittedAt(Instant committedAt) { this.committedAt = committedAt; }

	public Instant getRevertedAt() { return revertedAt; }
	public void setRevertedAt(Instant revertedAt) { this.revertedAt = revertedAt; }
}