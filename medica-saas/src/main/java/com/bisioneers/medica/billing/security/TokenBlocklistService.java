package com.bisioneers.medica.billing.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocklist en memoria para tokens JWT invalidados (logout).
 *
 * Funciona guardando el jti (JWT ID) de tokens revocados junto con
 * su fecha de expiración. Una vez que un token expira naturalmente,
 * se limpia del mapa para no consumir memoria indefinidamente.
 *
 * Para producción con múltiples instancias: migrar a Redis/DB.
 * En MVP con una sola instancia: ConcurrentHashMap es suficiente.
 */
@Service
public class TokenBlocklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlocklistService.class);

    /**
     * Map de jti → expiración del token.
     * Solo necesitamos mantener el jti hasta que el token expire naturalmente.
     */
    private final Map<String, Instant> blocklist = new ConcurrentHashMap<>();

    /**
     * Revoca un token agregando su jti al blocklist.
     *
     * @param jti       JWT ID único del token
     * @param expiresAt cuándo expira el token (para limpieza automática)
     */
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) return;
        blocklist.put(jti, expiresAt);
        log.debug("Token revoked: jti={}", jti);
    }

    /**
     * Verifica si un token está en el blocklist.
     */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return blocklist.containsKey(jti);
    }

    /**
     * Limpia tokens expirados del blocklist cada 15 minutos.
     * No tiene sentido mantener en memoria tokens que ya expiraron.
     */
    @Scheduled(fixedDelay = 900_000) // 15 min
    public void cleanup() {
        Instant now = Instant.now();
        int before = blocklist.size();
        blocklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - blocklist.size();
        if (removed > 0) {
            log.info("Token blocklist cleanup: removed {} expired entries, {} remaining",
                    removed, blocklist.size());
        }
    }

    /**
     * Tamaño actual del blocklist (para monitoring/actuator).
     */
    public int size() {
        return blocklist.size();
    }
}
