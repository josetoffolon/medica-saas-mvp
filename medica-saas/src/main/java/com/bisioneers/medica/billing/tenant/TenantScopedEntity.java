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
import jakarta.persistence.PrePersist;

@MappedSuperclass
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = java.util.UUID.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantScopedEntity extends AuditedEntity {

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "tenant_id", nullable = false, columnDefinition = "BINARY(16)")
  private UUID tenantId;

  @PrePersist
  void prePersistTenant() {
    if (tenantId == null) {
      UUID current = TenantContext.getTenantId();
      if (current != null) tenantId = current;
    }
  }

  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
}