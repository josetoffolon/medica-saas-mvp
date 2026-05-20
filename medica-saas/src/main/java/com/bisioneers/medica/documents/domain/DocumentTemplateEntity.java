package com.bisioneers.medica.documents.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Plantilla reutilizable para generar documentos médicos firmables.
 *
 * Pueden ser:
 *  - Pre-cargadas por el sistema (isSystem=true): consentimientos estándar
 *  - Creadas por el ADMIN del tenant (isSystem=false): plantillas custom
 *
 * El contenido es HTML con merge fields tipo {{patient.fullName}},
 * {{tenant.displayName}}, {{document.date}}, etc.
 */
@Entity
@Table(name = "document_template",
indexes = {
		@Index(name = "idx_template_tenant_type", columnList = "tenant_id,document_type"),
		@Index(name = "idx_template_tenant_active", columnList = "tenant_id,active")
})
public class DocumentTemplateEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	/**
	 * Tipo de documento. Valores estándar:
	 *  - CONSENT_GENERAL: Consentimiento general de procedimientos estéticos
	 *  - CONSENT_TIRZEPATIDA: Consentimiento específico tirzepatida + B12
	 *  - FOLLOWUP: Seguimiento mensual
	 *  - CUSTOM: plantilla personalizada del tenant
	 */
	@Column(name = "document_type", nullable = false, length = 50)
	private String documentType;

	@Lob
	@Column(name = "content_html", nullable = false, columnDefinition = "LONGTEXT")
	private String contentHtml;

	@Column(length = 500)
	private String description;

	@Column(nullable = false)
	private int version = 1;

	@Column(nullable = false)
	private boolean active = true;

	/** True = plantilla pre-cargada del sistema, no se puede editar/eliminar */
	@Column(name = "is_system", nullable = false)
	private boolean isSystem = false;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDocumentType() { return documentType; }
	public void setDocumentType(String documentType) { this.documentType = documentType; }
	public String getContentHtml() { return contentHtml; }
	public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public int getVersion() { return version; }
	public void setVersion(int version) { this.version = version; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
	public boolean isSystem() { return isSystem; }
	public void setSystem(boolean system) { isSystem = system; }
}
