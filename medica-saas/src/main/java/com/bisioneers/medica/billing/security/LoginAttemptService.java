package com.bisioneers.medica.billing.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lockout/throttling en memoria por clave (email, IP, etc.).
 *
 * Ventana deslizante: si pasan {attemptWindow} sin fallos nuevos, el contador
 * se reinicia. Al alcanzar {maxAttempts} fallos dentro de la ventana, la clave
 * queda bloqueada durante {lockDuration}.
 *
 * MVP de una sola instancia → ConcurrentHashMap basta.
 * Para multi-instancia: migrar a Redis (mismo contrato).
 */
@Service
public class LoginAttemptService {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

	private final int maxAttempts;
	private final Duration lockDuration;
	private final Duration attemptWindow;

	private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

	public LoginAttemptService(
			@Value("${security.login.max-attempts:5}") int maxAttempts,
			@Value("${security.login.lock-minutes:15}") long lockMinutes,
			@Value("${security.login.attempt-window-minutes:15}") long windowMinutes) {
		this.maxAttempts = maxAttempts;
		this.lockDuration = Duration.ofMinutes(lockMinutes);
		this.attemptWindow = Duration.ofMinutes(windowMinutes);
	}

	public boolean isBlocked(String key) {
		Attempt a = attempts.get(key);
		if (a == null || a.lockedUntil == null) return false;
		if (Instant.now().isBefore(a.lockedUntil)) return true;
		attempts.remove(key); // bloqueo expirado
		return false;
	}

	public void recordFailure(String key) {
		Instant now = Instant.now();
		attempts.compute(key, (k, a) -> {
			if (a == null) a = new Attempt();
			if (a.windowStart == null || Duration.between(a.windowStart, now).compareTo(attemptWindow) > 0) {
				a.count.set(0);
				a.windowStart = now;
			}
			int count = a.count.incrementAndGet();
			if (count >= maxAttempts) {
				a.lockedUntil = now.plus(lockDuration);
				log.warn("Clave '{}' bloqueada tras {} intentos fallidos hasta {}", key, count, a.lockedUntil);
			}
			return a;
		});
	}

	public void recordSuccess(String key) {
		attempts.remove(key);
	}

	@Scheduled(fixedDelay = 600_000) // cada 10 min
	public void cleanup() {
		Instant now = Instant.now();
		attempts.entrySet().removeIf(e ->
		e.getValue().lockedUntil != null && now.isAfter(e.getValue().lockedUntil));
	}

	private static class Attempt {
		final AtomicInteger count = new AtomicInteger(0);
		volatile Instant windowStart;
		volatile Instant lockedUntil;
	}
}
