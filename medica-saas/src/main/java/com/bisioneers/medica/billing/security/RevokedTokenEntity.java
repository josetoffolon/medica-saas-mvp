package com.bisioneers.medica.billing.security;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Token JWT revocado (logout / rotación de refresh).
 *
 * Fuente de verdad persistente: a diferencia del blocklist en memoria,
 * las revocaciones sobreviven a reinicios. Una entrada se puede borrar
 * solo cuando el token ya expiró naturalmente (no hay nada que revocar
 * de un token muerto).
 *
 * La PK es el jti, así la revocación es idempotente (revocar dos veces
 * el mismo token no crea duplicados).
 *
 * NO extiende TenantScopedEntity: es infraestructura de seguridad global,
 * no datos de tenant.
 */
@Entity
@Table(name = "revoked_token",
indexes = @Index(name = "idx_revoked_expires", columnList = "expires_at"))
public class RevokedTokenEntity {

	/** jti (JWT ID) del token revocado. */
	@Id
	@Column(name = "jti", length = 64)
	private String jti;

	/** Cuándo expira el token; permite limpiar la fila cuando ya no hace falta. */
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at", nullable = false)
	private Instant revokedAt;

	public RevokedTokenEntity() {}

	public RevokedTokenEntity(String jti, Instant expiresAt) {
		this.jti = jti;
		this.expiresAt = expiresAt;
		this.revokedAt = Instant.now();
	}

	public String getJti() { 
		return jti;
	}
	public void setJti(String jti) {
		this.jti = jti; 
	}
	public Instant getExpiresAt() {
		return expiresAt;
	}
	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}
	public Instant getRevokedAt() {
		return revokedAt;
	}
	public void setRevokedAt(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}
}