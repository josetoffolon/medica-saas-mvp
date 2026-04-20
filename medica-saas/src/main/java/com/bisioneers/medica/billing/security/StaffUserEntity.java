package com.bisioneers.medica.billing.security;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bisioneers.medica.billing.audit.AuditedEntity;

@Entity
@Table(name = "staff_user",
indexes = @Index(name = "idx_staff_email", columnList = "email", unique = true))
public class StaffUserEntity extends AuditedEntity{

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false, length = 160, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 20)
	private String role;

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "tenant_id", columnDefinition = "BINARY(16)", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "mfa_enabled", nullable = false)
	private boolean mfaEnabled = false;

	@Column(name = "mfa_secret", length = 64)
	private String mfaSecret;

	@Column(name = "mfa_activated_at")
	private Instant mfaActivatedAt;

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
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
	 * @return the email
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * @param email the email to set
	 */
	public void setEmail(String email) {
		this.email = email;
	}

	/**
	 * @return the passwordHash
	 */
	public String getPasswordHash() {
		return passwordHash;
	}

	/**
	 * @param passwordHash the passwordHash to set
	 */
	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	/**
	 * @return the role
	 */
	public String getRole() {
		return role;
	}

	/**
	 * @param role the role to set
	 */
	public void setRole(String role) {
		this.role = role;
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
	 * @return the enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @param enabled the enabled to set
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isMfaEnabled() { return mfaEnabled; }
	public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

	public String getMfaSecret() { return mfaSecret; }
	public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

	public Instant getMfaActivatedAt() { return mfaActivatedAt; }
	public void setMfaActivatedAt(Instant mfaActivatedAt) { this.mfaActivatedAt = mfaActivatedAt; }

}
