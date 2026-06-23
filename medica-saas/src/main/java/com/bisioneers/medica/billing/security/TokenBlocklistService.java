package com.bisioneers.medica.billing.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocklist de tokens JWT revocados (logout / rotación de refresh).
 *
 * CAMBIO (#7): la fuente de verdad ahora es la tabla revoked_token (MySQL).
 * Las revocaciones sobreviven a reinicios — antes vivían solo en memoria y
 * un token "revocado" revivía al reiniciar el servidor.
 *
 * El ConcurrentHashMap se conserva como CACHÉ de lectura delante de la BD:
 * isRevoked() se ejecuta en cada validación de JWT (hot path), así que
 * evitamos pegarle a MySQL en cada request. El caché se rehidrata al
 * arrancar y se actualiza en cada revoke().
 *
 * Consistencia multi-instancia: con varias instancias, una revocación hecha
 * en la instancia A no aparece en el caché de B hasta el refresco programado.
 * Para cierre inmediato cross-instancia, migrar a Redis pub/sub. En MVP de
 * una instancia es exacto.
 */
@Service
public class TokenBlocklistService {

	private static final Logger log = LoggerFactory.getLogger(TokenBlocklistService.class);

	private final RevokedTokenRepository repository;

	/** Caché jti → expiración. Espejo de las filas vigentes en BD. */
	private final Map<String, Instant> cache = new ConcurrentHashMap<>();

	public TokenBlocklistService(RevokedTokenRepository repository) {
		this.repository = repository;
	}

	/** Al arrancar: cargar las revocaciones vigentes desde BD al caché. */
	@PostConstruct
	@Transactional(readOnly = true)
	public void warmCache() {
		Instant now = Instant.now();
		repository.findActiveJtis(now).forEach(jti -> cache.put(jti, Instant.MAX));
		log.info("Token blocklist rehidratado desde BD: {} revocaciones vigentes", cache.size());
	}

	/**
	 * Revoca un token: persiste en BD y actualiza el caché.
	 * Idempotente (la PK es el jti).
	 */
	@Transactional
	public void revoke(String jti, Instant expiresAt) {
		if (jti == null || jti.isBlank()) return;
		Instant exp = expiresAt != null ? expiresAt : Instant.now().plusSeconds(7 * 24 * 3600);
		try {
			repository.save(new RevokedTokenEntity(jti, exp));
			cache.put(jti, exp);
			log.debug("Token revocado (persistido): jti={}", jti);
		} catch (Exception e) {
			// Si falla la BD, al menos revocamos en memoria para esta instancia
			cache.put(jti, exp);
			log.error("No se pudo persistir revocación de jti={}: {}", jti, e.getMessage());
		}
	}

	/** ¿Está revocado? Lee del caché (hot path, sin tocar BD). */
	public boolean isRevoked(String jti) {
		if (jti == null || jti.isBlank()) return false;
		return cache.containsKey(jti);
	}

	/**
	 * Limpieza periódica: borra de BD los tokens ya expirados y purga el caché.
	 * Un token expirado no necesita seguir revocado (ya no es válido por sí mismo).
	 */
	@Scheduled(fixedDelay = 900_000) // 15 min
	@Transactional
	public void cleanup() {
		Instant now = Instant.now();
		int removedDb = repository.deleteExpired(now);

		int before = cache.size();
		cache.entrySet().removeIf(e ->
		e.getValue() != Instant.MAX && e.getValue().isBefore(now));
		int removedCache = before - cache.size();

		// Refresco periódico del caché desde BD (recoge revocaciones de otras
		// instancias en despliegues multi-nodo).
		repository.findActiveJtis(now).forEach(jti -> cache.putIfAbsent(jti, Instant.MAX));

		if (removedDb > 0 || removedCache > 0) {
			log.info("Blocklist cleanup: {} filas BD y {} entradas de caché eliminadas, {} vigentes",
					removedDb, removedCache, cache.size());
		}
	}

	public int size() {
		return cache.size();
	}
}