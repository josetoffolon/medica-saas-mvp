package com.bisioneers.medica.billing.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RevokedTokenRepository extends JpaRepository<RevokedTokenEntity, String> {

    /** jtis aún vigentes (no expirados) — para rehidratar el caché al arrancar. */
    @Query("SELECT r.jti FROM RevokedTokenEntity r WHERE r.expiresAt > :now")
    List<String> findActiveJtis(@Param("now") Instant now);

    /** Limpieza: borra revocaciones de tokens que ya expiraron. */
    @Modifying
    @Query("DELETE FROM RevokedTokenEntity r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}