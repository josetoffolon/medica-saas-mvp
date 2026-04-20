package com.bisioneers.medica.billing.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén temporal de sesiones MFA pendientes.
 *
 * Cuando un usuario con MFA habilitado hace login con email+password,
 * se le entrega un mfaSessionToken (válido 5 minutos) que debe presentar
 * junto con el código TOTP en POST /api/auth/mfa/verify.
 *
 * NO usar este token como sesión real — solo para completar el flujo MFA.
 *
 * En producción con múltiples instancias, migrar a Redis.
 */
@Component
public class MfaSessionStore {

    private static final long SESSION_TTL_SECONDS = 300; // 5 minutos
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, MfaSession> sessions = new ConcurrentHashMap<>();

    /**
     * Crea una sesión MFA pendiente y retorna el token a entregarle al cliente.
     */
    public String createSession(UUID userId, UUID tenantId, String email) {
        String token = generateToken();
        MfaSession session = new MfaSession(
                userId, tenantId, email,
                Instant.now().plusSeconds(SESSION_TTL_SECONDS)
        );
        sessions.put(token, session);
        return token;
    }

    /**
     * Consume una sesión MFA. Retorna la sesión si es válida y la elimina,
     * o null si el token no existe o ya expiró.
     *
     * Una sesión solo puede usarse una vez (se elimina al consumir).
     */
    public MfaSession consume(String token) {
        if (token == null) return null;
        MfaSession session = sessions.remove(token);
        if (session == null) return null;
        if (Instant.now().isAfter(session.expiresAt)) return null;
        return session;
    }

    /** Cleanup periódico de sesiones expiradas */
    @Scheduled(fixedDelay = 60_000) // cada minuto
    public void cleanup() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record MfaSession(UUID userId, UUID tenantId, String email, Instant expiresAt) {}
}
