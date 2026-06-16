package com.bisioneers.medica.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache de SubscriptionStatusSnapshot con TTL corto.
 *
 * El SubscriptionEnforcementFilter corre en CADA request autenticado, así que
 * cachear el estado unos segundos elimina casi todas las lecturas a BD del
 * hot path. El TTL corto (default 30s) mantiene el estado razonablemente fresco;
 * además se invalida explícitamente al activar una suscripción pagada.
 *
 * Una sola instancia MVP → ConcurrentHashMap basta. Multi-instancia → Redis.
 */
@Component
public class SubscriptionStatusCache {

    private final Duration ttl;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    public SubscriptionStatusCache(
            @Value("${billing.status-cache.ttl-seconds:30}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Devuelve el snapshot cacheado si está vigente; si no, ejecuta el loader,
     * cachea el resultado y lo devuelve.
     */
    public SubscriptionStatusSnapshot get(UUID tenantId,
            Supplier<SubscriptionStatusSnapshot> loader) {
        Instant now = Instant.now();
        Entry entry = cache.get(tenantId);
        if (entry != null && now.isBefore(entry.expiresAt)) {
            return entry.snapshot;
        }
        // Miss o expirado. Un pequeño thundering-herd al expirar es aceptable
        // (varias cargas concurrentes, last-write-wins; sin problema de correctitud).
        SubscriptionStatusSnapshot fresh = loader.get();
        cache.put(tenantId, new Entry(fresh, now.plus(ttl)));
        return fresh;
    }

    /** Invalida el cache de un tenant (ej: tras activar suscripción pagada). */
    public void invalidate(UUID tenantId) {
        cache.remove(tenantId);
    }

    @Scheduled(fixedDelay = 300_000) // cada 5 min
    public void cleanup() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }

    private record Entry(SubscriptionStatusSnapshot snapshot, Instant expiresAt) {}
}
