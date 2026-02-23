
package com.bisioneers.medica.billing.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bisioneers.medica.billing.audit.AuditedEntity;

import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription",
       indexes = @Index(name="idx_subscription_status", columnList="status"))
public class SubscriptionEntity extends AuditedEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "tenant_id", columnDefinition = "BINARY(16)")
    private UUID tenantId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "last_transaction_id", columnDefinition = "BINARY(16)")
    private UUID lastTransactionId;


    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) status = "INACTIVE";
    }


	/**
	 * @return the tenantId
	 */
	public UUID getTenantId() {
		return tenantId;
	}

	/**
	 * @param tenantId the tenantId to set
	 */
	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}

	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * @return the currentPeriodStart
	 */
	public Instant getCurrentPeriodStart() {
		return currentPeriodStart;
	}

	/**
	 * @param currentPeriodStart the currentPeriodStart to set
	 */
	public void setCurrentPeriodStart(Instant currentPeriodStart) {
		this.currentPeriodStart = currentPeriodStart;
	}

	/**
	 * @return the currentPeriodEnd
	 */
	public Instant getCurrentPeriodEnd() {
		return currentPeriodEnd;
	}

	/**
	 * @param currentPeriodEnd the currentPeriodEnd to set
	 */
	public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
		this.currentPeriodEnd = currentPeriodEnd;
	}

	/**
	 * @return the lastTransactionId
	 */
	public UUID getLastTransactionId() {
		return lastTransactionId;
	}

	/**
	 * @param lastTransactionId the lastTransactionId to set
	 */
	public void setLastTransactionId(UUID lastTransactionId) {
		this.lastTransactionId = lastTransactionId;
	}

}

