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
	private final int gracePeriodDays;

	public SubscriptionService(
			SubscriptionRepository subscriptionRepository,
			@Value("${billing.grace-period-days:5}") int gracePeriodDays
			) {
		this.subscriptionRepository = subscriptionRepository;
		this.gracePeriodDays = gracePeriodDays;
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
	@Transactional(readOnly = true)
	public boolean isActive(UUID tenantId) {
		return subscriptionRepository.findById(tenantId)
				.map(sub -> {
					if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) return false;
					Instant end = sub.getCurrentPeriodEnd();
					if (end == null) return false;

					Instant now = Instant.now();

					// Periodo vigente → activo
					if (end.isAfter(now)) return true;

					// Periodo vencido pero dentro de grace period → aún activo
					Instant graceEnd = end.plus(gracePeriodDays, ChronoUnit.DAYS);
					return graceEnd.isAfter(now);
				})
				.orElse(false);
	}

	/**
	 * ¿El tenant está en periodo de gracia?
	 *
	 * True cuando el periodo ya venció pero aún tiene días de gracia.
	 * El filter usa esto para agregar un header de warning al response.
	 */
	@Transactional(readOnly = true)
	public boolean isInGracePeriod(UUID tenantId) {
		return subscriptionRepository.findById(tenantId)
				.map(sub -> {
					if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) return false;
					Instant end = sub.getCurrentPeriodEnd();
					if (end == null) return false;

					Instant now = Instant.now();

					// Si el periodo aún no venció, no está en gracia
					if (end.isAfter(now)) return false;

					// Si ya venció, verificar si está dentro de grace period
					Instant graceEnd = end.plus(gracePeriodDays, ChronoUnit.DAYS);
					return graceEnd.isAfter(now);
				})
				.orElse(false);
	}

	/**
	 * Calcula cuándo termina el grace period para un tenant.
	 * Retorna null si no tiene suscripción o no está en gracia.
	 */
	@Transactional(readOnly = true)
	public Instant getGracePeriodEnd(UUID tenantId) {
		return subscriptionRepository.findById(tenantId)
				.map(sub -> {
					Instant end = sub.getCurrentPeriodEnd();
					if (end == null) return null;
					return end.plus(gracePeriodDays, ChronoUnit.DAYS);
				})
				.orElse(null);
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
		sub.setCurrentPeriodEnd(start.plus(1, ChronoUnit.MONTHS));
		sub.setLastTransactionId(txId);

		subscriptionRepository.save(sub);

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