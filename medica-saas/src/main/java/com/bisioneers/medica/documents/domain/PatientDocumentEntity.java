package com.bisioneers.medica.documents.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Documento generado para un paciente específico desde una versión
 * PUBLISHED de un ConsentTemplate.
 *
 * Ciclo de vida:
 *  DRAFT          → renderizado, contenido editable (correcciones menores)
 *  READY_TO_SIGN  → PDF generado, listo para firmar
 *  SIGNED         → Firmado y archivado, inmutable
 *  ARCHIVED       → Movido a histórico
 *
 * El contenido HTML renderizado se almacena como snapshot. Aunque la versión
 * del consent se archive después, este documento conserva el texto original
 * con los datos del paciente al momento de generación.
 *
 * REFACTOR (fusión consent + documents):
 *  - Antes: template_id → DocumentTemplateEntity (eliminado)
 *  - Ahora: consent_template_version_id → ConsentTemplateVersionEntity (módulo consent)
 *  - Snapshot inmutable + referencia a la versión exacta firmada
 */
@Entity
@Table(name = "patient_document",
indexes = {
		@Index(name = "idx_pdoc_tenant_patient", columnList = "tenant_id,patient_id"),
		@Index(name = "idx_pdoc_tenant_status", columnList = "tenant_id,status"),
		@Index(name = "idx_pdoc_tenant_version", columnList = "tenant_id,consent_template_version_id")
})
public class PatientDocumentEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "patient_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID patientId;

	/** FK a ConsentTemplateVersionEntity. La versión exacta firmada. */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "consent_template_version_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID consentTemplateVersionId;

	/** Cache del ID de la cabecera del template, para listings/filtros sin JOIN. */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "consent_template_id", columnDefinition = "BINARY(16)")
	private UUID consentTemplateId;

	/** Snapshot del nombre + versión al momento de generación. */
	@Column(name = "template_name", length = 200)
	private String templateName;

	@Column(name = "template_version_number")
	private Integer templateVersionNumber;

	@Column(nullable = false, length = 300)
	private String title;

	/**
	 * HTML renderizado con merge fields ya resueltos.
	 * Snapshot inmutable — si la versión cambia después, este queda intacto.
	 */
	@Lob
	@Column(name = "rendered_html", nullable = false, columnDefinition = "MEDIUMTEXT")
	private String renderedHtml;

	/** DRAFT | READY_TO_SIGN | SIGNED | ARCHIVED */
	@Column(nullable = false, length = 20)
	private String status = "DRAFT";

	@Column(name = "pdf_storage_key", length = 500)
	private String pdfStorageKey;

	@Column(name = "signed_pdf_storage_key", length = 500)
	private String signedPdfStorageKey;

	/** SHA-256 hex del PDF firmado para integridad. */
	@Column(name = "integrity_hash", length = 64)
	private String integrityHash;

	/** DIGITAL | SCANNED */
	@Column(name = "signature_method", length = 20)
	private String signatureMethod;

	@Column(name = "generated_at")
	private Instant generatedAt;

	@Column(name = "signed_at")
	private Instant signedAt;

	// ─── Audit trail de la firma ───

	@Column(name = "signer_name", length = 250)
	private String signerName;

	@Column(name = "signer_document", length = 50)
	private String signerDocument;

	@Column(name = "signer_ip", length = 45)
	private String signerIp;

	@Column(name = "signer_user_agent", length = 500)
	private String signerUserAgent;

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
	public UUID getConsentTemplateVersionId() { return consentTemplateVersionId; }
	public void setConsentTemplateVersionId(UUID id) { this.consentTemplateVersionId = id; }
	public UUID getConsentTemplateId() { return consentTemplateId; }
	public void setConsentTemplateId(UUID id) { this.consentTemplateId = id; }
	public String getTemplateName() { return templateName; }
	public void setTemplateName(String name) { this.templateName = name; }
	public Integer getTemplateVersionNumber() { return templateVersionNumber; }
	public void setTemplateVersionNumber(Integer n) { this.templateVersionNumber = n; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getRenderedHtml() { return renderedHtml; }
	public void setRenderedHtml(String renderedHtml) { this.renderedHtml = renderedHtml; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getPdfStorageKey() { return pdfStorageKey; }
	public void setPdfStorageKey(String key) { this.pdfStorageKey = key; }
	public String getSignedPdfStorageKey() { return signedPdfStorageKey; }
	public void setSignedPdfStorageKey(String key) { this.signedPdfStorageKey = key; }
	public String getIntegrityHash() { return integrityHash; }
	public void setIntegrityHash(String h) { this.integrityHash = h; }
	public String getSignatureMethod() { return signatureMethod; }
	public void setSignatureMethod(String m) { this.signatureMethod = m; }
	public Instant getGeneratedAt() { return generatedAt; }
	public void setGeneratedAt(Instant at) { this.generatedAt = at; }
	public Instant getSignedAt() { return signedAt; }
	public void setSignedAt(Instant at) { this.signedAt = at; }
	public String getSignerName() { return signerName; }
	public void setSignerName(String n) { this.signerName = n; }
	public String getSignerDocument() { return signerDocument; }
	public void setSignerDocument(String d) { this.signerDocument = d; }
	public String getSignerIp() { return signerIp; }
	public void setSignerIp(String ip) { this.signerIp = ip; }
	public String getSignerUserAgent() { return signerUserAgent; }
	public void setSignerUserAgent(String ua) { this.signerUserAgent = ua; }
	public UUID getStaffUserId() { return staffUserId; }
	public void setStaffUserId(UUID id) { this.staffUserId = id; }
}
