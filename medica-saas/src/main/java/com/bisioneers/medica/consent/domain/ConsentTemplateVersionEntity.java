package com.bisioneers.medica.consent.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Versión de una plantilla. INMUTABLE una vez publicada.
 *
 * Reglas:
 *  - DRAFT       → editable libremente
 *  - PUBLISHED   → @PreUpdate bloquea cambios al título o al contenido
 *  - ARCHIVED    → tampoco editable, persiste para auditoría de firmas pasadas
 *
 * Auditoría (mejora #7):
 *  - lastEditedByUserId/lastEditedAt → trazabilidad de DRAFTS
 *  - publishedByUserId/publishedAt   → trazabilidad de PUBLISHED
 */
@Entity
@Table(name = "consent_template_version",
indexes = {
		@Index(name = "idx_consent_version_tenant", columnList = "tenant_id"),
		@Index(name = "idx_consent_version_template_status", columnList = "template_id,status")
},
uniqueConstraints = {
		@UniqueConstraint(name = "uk_consent_version", columnNames = {"template_id", "version_number"})
})
public class ConsentTemplateVersionEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "template_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID templateId;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Column(nullable = false, length = 300)
	private String title;

	/** HTML ya sanitizado por Jsoup en el service. Nunca confiar en el cliente. */
	@Lob
	@Column(name = "content_html", nullable = false, columnDefinition = "MEDIUMTEXT")
	private String contentHtml;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConsentVersionStatus status = ConsentVersionStatus.DRAFT;

	@Column(name = "published_at")
	private Instant publishedAt;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "published_by_user_id", columnDefinition = "BINARY(16)")
	private UUID publishedByUserId;

	// ─── Auditoría adicional (mejora #7) ───

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "last_edited_by_user_id", columnDefinition = "BINARY(16)")
	private UUID lastEditedByUserId;

	@Column(name = "last_edited_at")
	private Instant lastEditedAt;

	// ─── Snapshots para detectar mutaciones prohibidas en @PreUpdate ───

	@Transient
	private ConsentVersionStatus originalStatus;

	@Transient
	private String originalContentHtml;

	@Transient
	private String originalTitle;

	@PrePersist
	void prePersistVersion() {
		if (id == null) id = UUID.randomUUID();
		originalStatus = status;
		originalContentHtml = contentHtml;
		originalTitle = title;
	}

	@PostLoad
	void postLoadVersion() {
		originalStatus = status;
		originalContentHtml = contentHtml;
		originalTitle = title;
	}

	/**
	 * Defensa en profundidad. Si por bug o llamada directa al repo alguien intenta
	 * mutar una versión publicada/archivada, JPA lanza error antes del flush.
	 */
	@PreUpdate
	void preUpdateVersion() {
		if (originalStatus == ConsentVersionStatus.PUBLISHED
				|| originalStatus == ConsentVersionStatus.ARCHIVED) {

			boolean contentChanged =
					!java.util.Objects.equals(originalContentHtml, contentHtml)
					|| !java.util.Objects.equals(originalTitle, title);

			boolean illegalStatusTransition =
					originalStatus == ConsentVersionStatus.PUBLISHED
					&& status != ConsentVersionStatus.PUBLISHED
					&& status != ConsentVersionStatus.ARCHIVED;

			if (contentChanged) {
				throw new IllegalStateException(
						"No se puede modificar el contenido de una versión " + originalStatus +
						" (id=" + id + ")");
			}
			if (illegalStatusTransition || originalStatus == ConsentVersionStatus.ARCHIVED) {
				throw new IllegalStateException(
						"Transición de estado no permitida: " + originalStatus + " → " + status);
			}
		}
	}

	// Getters / setters
	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getTemplateId() { return templateId; }
	public void setTemplateId(UUID templateId) { this.templateId = templateId; }
	public int getVersionNumber() { return versionNumber; }
	public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getContentHtml() { return contentHtml; }
	public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
	public ConsentVersionStatus getStatus() { return status; }
	public void setStatus(ConsentVersionStatus status) { this.status = status; }
	public Instant getPublishedAt() { return publishedAt; }
	public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
	public UUID getPublishedByUserId() { return publishedByUserId; }
	public void setPublishedByUserId(UUID publishedByUserId) { this.publishedByUserId = publishedByUserId; }
	public UUID getLastEditedByUserId() { return lastEditedByUserId; }
	public void setLastEditedByUserId(UUID lastEditedByUserId) { this.lastEditedByUserId = lastEditedByUserId; }
	public Instant getLastEditedAt() { return lastEditedAt; }
	public void setLastEditedAt(Instant lastEditedAt) { this.lastEditedAt = lastEditedAt; }
}
