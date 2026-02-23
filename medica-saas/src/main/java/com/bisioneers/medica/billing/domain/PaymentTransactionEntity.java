package com.bisioneers.medica.billing.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_transaction",
    indexes = {
        @Index(name="idx_pt_tenant_status", columnList="tenant_id,status"),
        @Index(name="idx_pt_provider_ref", columnList="provider_ref", unique = true)
    }
)
public class PaymentTransactionEntity extends TenantScopedEntity {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
  private UUID id;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String provider;

  @Column(name="provider_ref", unique = true)
  private String providerRef;

  @Column(nullable = false)
  private String status;

  @Lob
  @Column(name="payload_json", columnDefinition="LONGTEXT")
  private String payloadJson;

  
  @PrePersist
  void prePersistI(){
	  if (id==null) id = UUID.randomUUID();
  }

  /**
   * @return the id
   */
  public UUID getId() {
	return id;
  }

  /**
   * @param id the id to set
   */
  public void setId(UUID id) {
	this.id = id;
  }

  /**
   * @return the amount
   */
  public BigDecimal getAmount() {
	return amount;
  }

  /**
   * @param amount the amount to set
   */
  public void setAmount(BigDecimal amount) {
	this.amount = amount;
  }

  /**
   * @return the currency
   */
  public String getCurrency() {
	return currency;
  }

  /**
   * @param currency the currency to set
   */
  public void setCurrency(String currency) {
	this.currency = currency;
  }

  /**
   * @return the provider
   */
  public String getProvider() {
	return provider;
  }

  /**
   * @param provider the provider to set
   */
  public void setProvider(String provider) {
	this.provider = provider;
  }

  /**
   * @return the providerRef
   */
  public String getProviderRef() {
	return providerRef;
  }

  /**
   * @param providerRef the providerRef to set
   */
  public void setProviderRef(String providerRef) {
	this.providerRef = providerRef;
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
   * @return the payloadJson
   */
  public String getPayloadJson() {
	return payloadJson;
  }

  /**
   * @param payloadJson the payloadJson to set
   */
  public void setPayloadJson(String payloadJson) {
	this.payloadJson = payloadJson;
  }
  
}