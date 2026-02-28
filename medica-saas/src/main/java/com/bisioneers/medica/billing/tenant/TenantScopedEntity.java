package com.bisioneers.medica.billing.tenant;

import java.util.UUID;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bisioneers.medica.billing.audit.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;

/**
 * Entidad base para todas las entidades multi-tenant.
 *
 * Toda entidad que pertenece a un tenant debe extender esta clase.
 * Proporciona:
 *   1. Columna tenant_id con @Filter de Hibernate (queries automáticas por tenant)
 *   2. @PrePersist: asigna tenantId desde TenantContext si no se especifica
 *   3. @PreUpdate: previene que se cambie el tenantId de una entidad existente
 *
 * CAMBIOS vs versión anterior:
 * - Agregado @PreUpdate con validación de tenantId inmutable
 * - Agregado campo @Transient originalTenantId para detectar mutaciones
 * - Mejor manejo de @PrePersist con validación obligatoria
 */
@MappedSuperclass
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = java.util.UUID.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantScopedEntity extends AuditedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID tenantId;

    /**
     * Guarda el tenantId original cuando la entidad se carga de la DB.
     * Usado por @PreUpdate para detectar intentos de cambio.
     */
    @Transient
    private UUID originalTenantId;

    /**
     * Al persistir: si no se asignó tenantId explícitamente,
     * lo toma del TenantContext (ThreadLocal seteado por TenantContextFilter).
     */
    @PrePersist
    void prePersistTenant() {
        if (tenantId == null) {
            UUID current = TenantContext.getTenantId();
            if (current != null) {
                tenantId = current;
            }
        }
        // Guardar como referencia para @PreUpdate
        originalTenantId = tenantId;
    }

    /**
     * Al cargar desde la DB: guarda el tenantId original.
     * Hibernate usa field access, así que el setter puede no llamarse.
     */
    @PostLoad
    void postLoadTenant() {
        originalTenantId = tenantId;
    }

    /**
     * Al actualizar: verifica que nadie haya cambiado el tenantId.
     * Un cambio de tenantId movería la entidad de un tenant a otro,
     * lo cual es un riesgo grave de seguridad.
     */
    @PreUpdate
    void preUpdateTenant() {
        if (originalTenantId != null && !originalTenantId.equals(tenantId)) {
            throw new IllegalStateException(
                    "Cannot change tenantId of an existing entity. " +
                    "Original: " + originalTenantId + ", attempted: " + tenantId
            );
        }
    }

    public UUID getTenantId() { return tenantId; }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
