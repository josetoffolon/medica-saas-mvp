package com.bisioneers.medica.documents.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Documento generado para un paciente específico desde una plantilla.
 *
 * Ciclo de vida:
 *  DRAFT          → Creado, contenido editable, NO firmado
 *  READY_TO_SIGN  → PDF generado, listo para firmar (canvas o impresión)
 *  SIGNED         → Firmado y archivado en R2, inmutable
 *  ARCHIVED       → Movido a histórico (mantenimiento futuro)
 *
 * El contenido HTML se almacena como snapshot, así si la plantilla
 * cambia después, el documento queda intacto con el texto firmado.
 */
@Entity
@Table(name = "patient_document",
indexes = {
		@Index(name = "idx_pdoc_tenant_patient", columnList = "tenant_id,patient_id"),
		@Index(name = "idx_pdoc_tenant_status", columnList = "tenant_id,status"),
		@Index(name = "idx_pdoc_tenant_type", columnList = "tenant_id,document_type")
})
public class PatientDocumentEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "patient_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID patientId;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "template_id", columnDefinition = "BINARY(16)")
	private UUID templateId;

	/** Snapshot del nombre de la plantilla al momento de generación */
	@Column(name = "template_name", length = 200)
	private String templateName;

	@Column(name = "document_type", nullable = false, length = 50)
	private String documentType;

	@Column(nullable = false, length = 300)
	private String title;

	/** Contenido HTML renderizado con merge fields ya reemplazados */
	@Lob
	@Column(name = "rendered_html", nullable = false, columnDefinition = "LONGTEXT")
	private String renderedHtml;

	/** DRAFT | READY_TO_SIGN | SIGNED | ARCHIVED */
	@Column(nullable = false, length = 20)
	private String status = "DRAFT";

	/** Key del PDF base sin firma en R2 (estado READY_TO_SIGN) */
	@Column(name = "pdf_storage_key", length = 500)
	private String pdfStorageKey;

	/** Key del PDF firmado en R2 (estado SIGNED) */
	@Column(name = "signed_pdf_storage_key", length = 500)
	private String signedPdfStorageKey;

	/** SHA-256 del PDF firmado, para integridad anti-manipulación */
	@Column(name = "integrity_hash", length = 64)
	private String integrityHash;

	/** DIGITAL | SCANNED — cómo se firmó */
	@Column(name = "signature_method", length = 20)
	private String signatureMethod;

	@Column(name = "generated_at")
	private Instant generatedAt;

	@Column(name = "signed_at")
	private Instant signedAt;

	/** Datos del firmante registrados al momento de la firma (audit) */
	@Column(name = "signer_name", length = 250)
	private String signerName;

	@Column(name = "signer_document", length = 50)
	private String signerDocument;

	@Column(name = "signer_ip", length = 45)
	private String signerIp;

	@Column(name = "signer_user_agent", length = 500)
	private String signerUserAgent;

	/** Staff que supervisó la firma */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "staff_user_id", columnDefinition = "BINARY(16)")
	private UUID staffUserId;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
		if (generatedAt == null) generatedAt = Instant.now();
	}

	// ─── Getters / Setters ──────────────────────────────────────

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getPatientId() { return patientId; }
	public void setPatientId(UUID patientId) { this.patientId = patientId; }
	public UUID getTemplateId() { return templateId; }
	public void setTemplateId(UUID templateId) { this.templateId = templateId; }
	public String getTemplateName() { return templateName; }
	public void setTemplateName(String templateName) { this.templateName = templateName; }
	public String getDocumentType() { return documentType; }
	public void setDocumentType(String documentType) { this.documentType = documentType; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getRenderedHtml() { return renderedHtml; }
	public void setRenderedHtml(String renderedHtml) { this.renderedHtml = renderedHtml; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getPdfStorageKey() { return pdfStorageKey; }
	public void setPdfStorageKey(String pdfStorageKey) { this.pdfStorageKey = pdfStorageKey; }
	public String getSignedPdfStorageKey() { return signedPdfStorageKey; }
	public void setSignedPdfStorageKey(String signedPdfStorageKey) { this.signedPdfStorageKey = signedPdfStorageKey; }
	public String getIntegrityHash() { return integrityHash; }
	public void setIntegrityHash(String integrityHash) { this.integrityHash = integrityHash; }
	public String getSignatureMethod() { return signatureMethod; }
	public void setSignatureMethod(String signatureMethod) { this.signatureMethod = signatureMethod; }
	public Instant getGeneratedAt() { return generatedAt; }
	public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
	public Instant getSignedAt() { return signedAt; }
	public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
	public String getSignerName() { return signerName; }
	public void setSignerName(String signerName) { this.signerName = signerName; }
	public String getSignerDocument() { return signerDocument; }
	public void setSignerDocument(String signerDocument) { this.signerDocument = signerDocument; }
	public String getSignerIp() { return signerIp; }
	public void setSignerIp(String signerIp) { this.signerIp = signerIp; }
	public String getSignerUserAgent() { return signerUserAgent; }
	public void setSignerUserAgent(String signerUserAgent) { this.signerUserAgent = signerUserAgent; }
	public UUID getStaffUserId() { return staffUserId; }
	public void setStaffUserId(UUID staffUserId) { this.staffUserId = staffUserId; }
}
