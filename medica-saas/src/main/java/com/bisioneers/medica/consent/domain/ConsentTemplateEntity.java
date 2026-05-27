package com.bisioneers.medica.consent.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Plantilla de consentimiento (cabecera).
 *
 * Una plantilla tiene N versiones; solo una está "publicada actualmente"
 * (current_version_id). El nombre y código son editables; el contenido
 * legal vive en las versiones inmutables.
 */
@Entity
@Table(name = "consent_template",
indexes = {
		@Index(name = "idx_consent_template_tenant_active", columnList = "tenant_id,active"),
		@Index(name = "idx_consent_template_tenant_order",  columnList = "tenant_id,display_order")
},
uniqueConstraints = {
		@UniqueConstraint(name = "uk_consent_template_tenant_code",
				columnNames = {"tenant_id", "code"})
})
public class ConsentTemplateEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	/** Slug interno único por tenant (ej: "botox-frontal", "fotos-medicas"). */
	@Column(nullable = false, length = 80)
	private String code;

	@Column(length = 500)
	private String description;

	/**
	 * Apunta a la versión PUBLISHED más reciente que se ofrece para firmar.
	 * Null mientras la primera versión esté en DRAFT.
	 */
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "current_version_id", columnDefinition = "BINARY(16)")
	private UUID currentVersionId;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "display_order", nullable = false)
	private int displayOrder = 0;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public UUID getCurrentVersionId() { return currentVersionId; }
	public void setCurrentVersionId(UUID currentVersionId) { this.currentVersionId = currentVersionId; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
	public int getDisplayOrder() { return displayOrder; }
	public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
