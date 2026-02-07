package com.bisioneers.medica.billing.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription")
public class SubscriptionEntity {

  @Id
  @Column(name = "tenant_id", nullable = false, length = 36)
  private UUID tenantId;

  @Column(nullable = false, length = 20)
  private String status; // ACTIVE | INACTIVE | PAST_DUE

  @Column(name = "current_period_start", nullable = false)
  private Instant currentPeriodStart;

  @Column(name = "current_period_end", nullable = false)
  private Instant currentPeriodEnd;

  @Column(name = "last_transaction_id", length = 36)
  private String lastTransactionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;

    if (status == null) status = "INACTIVE";
    if (currentPeriodStart == null) currentPeriodStart = now;
    if (currentPeriodEnd == null) currentPeriodEnd = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
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
  public String getLastTransactionId() {
	return lastTransactionId;
  }

  /**
   * @param lastTransactionId the lastTransactionId to set
   */
  public void setLastTransactionId(String lastTransactionId) {
	this.lastTransactionId = lastTransactionId;
  }

  /**
   * @return the createdAt
   */
  public Instant getCreatedAt() {
	return createdAt;
  }

  /**
   * @param createdAt the createdAt to set
   */
  public void setCreatedAt(Instant createdAt) {
	this.createdAt = createdAt;
  }

  /**
   * @return the updatedAt
   */
  public Instant getUpdatedAt() {
	return updatedAt;
  }

  /**
   * @param updatedAt the updatedAt to set
   */
  public void setUpdatedAt(Instant updatedAt) {
	this.updatedAt = updatedAt;
  }

}

