package com.bisioneers.medica.billing.security;

import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Crea una sesión MFA pendiente y retorna el token a entregarle al cliente.
     */
    public String createSession(UUID userId, UUID tenantId, String email) {
        String token = generateToken();
        sessions.put(token, new MfaSession(
                userId, tenantId, email,
                Instant.now().plusSeconds(SESSION_TTL_SECONDS),
                new AtomicInteger(0)));
        return token;
    }

    /** Devuelve la sesión SIN eliminarla. null si no existe o expiró. */
    public MfaSession peek(String token) {
        if (token == null) return null;
        MfaSession s = sessions.get(token);
        if (s == null) return null;
        if (Instant.now().isAfter(s.expiresAt())) { sessions.remove(token); return null; }
        return s;
    }

    /** Éxito: consume (elimina) la sesión. */
    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }

    /**
     * Fallo: incrementa el contador. Devuelve intentos restantes.
     * Si se agotan, la sesión se elimina (el usuario debe re-loguearse).
     */
    public int registerFailedAttempt(String token) {
        MfaSession s = peek(token);
        if (s == null) return 0;
        int used = s.attempts().incrementAndGet();
        int remaining = MAX_ATTEMPTS - used;
        if (remaining <= 0) {
            sessions.remove(token);
            return 0;
        }
        return remaining;
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

    public record MfaSession(UUID userId, UUID tenantId, String email, Instant expiresAt, AtomicInteger attempts) {}
}
