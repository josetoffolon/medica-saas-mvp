package com.bisioneers.medica.billing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bisioneers.medica.billing.domain.SubscriptionEntity;
import com.bisioneers.medica.billing.domain.SubscriptionRepository;
import com.bisioneers.medica.billing.pf.dto.SubscriptionStatusDto;

/**

- Servicio de suscripciones con soporte de grace period.
- 
- CAMBIOS vs versión anterior:
- - isActive() ahora incluye grace period (suscripción vencida + dentro de gracia = activa)
- - Nuevo: isInGracePeriod() para saber si el tenant está en periodo de gracia
- - Nuevo: getGracePeriodEnd() para calcular cuándo termina la gracia
- - getStatus() ahora retorna estado GRACE_PERIOD cuando aplica
- - Grace period configurable via billing.grace-period-days (default: 5)
- 
- Estados de suscripción:
- ACTIVE      → periodo vigente, acceso completo
- GRACE_PERIOD → periodo vencido pero dentro de los días de gracia
- PAST_DUE    → periodo vencido y gracia agotada
- INACTIVE    → nunca ha pagado o desactivada manualmente
- NONE        → no existe registro de suscripción
 */
@Service
public class SubscriptionService {

	private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

	private final SubscriptionRepository subscriptionRepository;
	private final SubscriptionStatusCache statusCache;
	private final int gracePeriodDays;

	public SubscriptionService(
			SubscriptionRepository subscriptionRepository,
			SubscriptionStatusCache statusCache,
			@Value("${billing.grace-period-days:5}") int gracePeriodDays
			) {
		this.subscriptionRepository = subscriptionRepository;
		this.statusCache = statusCache;
		this.gracePeriodDays = gracePeriodDays;
	}
	
	/**
	 * Snapshot de estado para el enforcement filter. Cacheado con TTL corto.
	 * Reemplaza las 3 lecturas previas (isActive/isInGracePeriod/getGracePeriodEnd)
	 * por una sola.
	 */
	public SubscriptionStatusSnapshot getEnforcementSnapshot(UUID tenantId) {
	    return statusCache.get(tenantId, () -> computeSnapshot(tenantId));
	}

	/**
	 * Calcula el snapshot desde UNA sola lectura de BD.
	 * (Sin @Transactional explícito: el findById de Spring Data ya gestiona su
	 * propia transacción y SubscriptionEntity no tiene asociaciones lazy.)
	 */
	private SubscriptionStatusSnapshot computeSnapshot(UUID tenantId) {
	    Instant now = Instant.now();

	    return subscriptionRepository.findById(tenantId)
	            .map(sub -> {
	                String rawStatus = sub.getStatus();
	                Instant end = sub.getCurrentPeriodEnd();

	                // No ACTIVE o sin fecha de fin → no puede usar el sistema
	                if (!"ACTIVE".equalsIgnoreCase(rawStatus) || end == null) {
	                    return new SubscriptionStatusSnapshot(false, false, null,
	                            rawStatus == null ? "INACTIVE" : rawStatus.toUpperCase());
	                }

	                Instant graceEnd = end.plus(gracePeriodDays, ChronoUnit.DAYS);

	                if (end.isAfter(now)) {
	                    // Periodo vigente
	                    return new SubscriptionStatusSnapshot(true, false, graceEnd, "ACTIVE");
	                }
	                if (graceEnd.isAfter(now)) {
	                    // Vencido pero dentro de la gracia
	                    return new SubscriptionStatusSnapshot(true, true, graceEnd, "GRACE_PERIOD");
	                }
	                // Vencido y gracia agotada
	                return new SubscriptionStatusSnapshot(false, false, graceEnd, "PAST_DUE");
	            })
	            .orElse(new SubscriptionStatusSnapshot(false, false, null, "NONE"));
	}

	/**
	 * ¿El tenant puede usar el sistema?
	 *
	 * Retorna true si:
	 * - La suscripción está ACTIVE y el periodo no ha vencido, O
	 * - La suscripción está ACTIVE pero venció hace menos de {gracePeriodDays} días
	 *
	 * El SubscriptionEnforcementFilter usa este método para decidir si
	 * bloquear con 402 o dejar pasar (con o sin warning).
	 */

	public boolean isActive(UUID tenantId) {
	    return getEnforcementSnapshot(tenantId).active();
	}

	/**
	 * ¿El tenant está en periodo de gracia?
	 *
	 * True cuando el periodo ya venció pero aún tiene días de gracia.
	 * El filter usa esto para agregar un header de warning al response.
	 */

	public boolean isInGracePeriod(UUID tenantId) {
	    return getEnforcementSnapshot(tenantId).inGracePeriod();
	}

	/**
	 * Calcula cuándo termina el grace period para un tenant.
	 * Retorna null si no tiene suscripción o no está en gracia.
	 */

	public Instant getGracePeriodEnd(UUID tenantId) {
	    return getEnforcementSnapshot(tenantId).graceEnd();
	}

	@Transactional
	public void activateFromPaidTransaction(UUID tenantId, UUID txId) {

		Instant now = Instant.now();

		SubscriptionEntity sub = subscriptionRepository.findById(tenantId)
				.orElseGet(() -> {
					SubscriptionEntity s = new SubscriptionEntity();
					s.setTenantId(tenantId);
					return s;
				});

		Instant start = now;
		Instant currentEnd = sub.getCurrentPeriodEnd();

		// Si ya estaba activa y aún no vence (incluyendo gracia), extiende desde el fin actual
		if ("ACTIVE".equalsIgnoreCase(sub.getStatus())
				&& currentEnd != null
				&& currentEnd.isAfter(now)) {
			start = currentEnd;
		}

		sub.setStatus("ACTIVE");
		sub.setCurrentPeriodStart(start);
		sub.setCurrentPeriodEnd(start.plus(30, ChronoUnit.DAYS));
		sub.setLastTransactionId(txId);

		subscriptionRepository.save(sub);
		statusCache.invalidate(tenantId);

		log.info("Subscription activated: tenant={}, period={} to {}",
				tenantId, sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd());
	}

	@Transactional(readOnly = true)
	public SubscriptionStatusDto getStatus(UUID tenantId) {

		Instant now = Instant.now();

		return subscriptionRepository.findById(tenantId)
				.map(sub -> {
					String status = sub.getStatus();

					if ("ACTIVE".equalsIgnoreCase(status)) {
						Instant end = sub.getCurrentPeriodEnd();
						if (end == null || !end.isAfter(now)) {
							// Venció — verificar grace period
							if (end != null) {
								Instant graceEnd = end.plus(gracePeriodDays, ChronoUnit.DAYS);
								if (graceEnd.isAfter(now)) {
									status = "GRACE_PERIOD";
								} else {
									status = "PAST_DUE";
								}
							} else {
								status = "PAST_DUE";
							}
						}
					}

					return new SubscriptionStatusDto(
							tenantId,
							status == null ? "INACTIVE" : status.toUpperCase(),
									sub.getCurrentPeriodStart(),
									sub.getCurrentPeriodEnd(),
									now
							);
				})
				.orElseGet(() -> new SubscriptionStatusDto(
						tenantId,
						"NONE",
						null,
						null,
						now
						));
	}
}