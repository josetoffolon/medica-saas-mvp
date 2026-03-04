package com.bisioneers.medica.notification.jobs;

import com.bisioneers.medica.billing.domain.SubscriptionEntity;
import com.bisioneers.medica.billing.domain.SubscriptionRepository;
import com.bisioneers.medica.billing.security.StaffUserEntity;
import com.bisioneers.medica.billing.security.StaffUserRepository;
import com.bisioneers.medica.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**

- Job programado que envía notificaciones de suscripción próxima a vencer.
- 
- Busca suscripciones ACTIVE que vencen en los próximos días y envía
- un aviso a los usuarios ADMIN del tenant correspondiente.
- 
- Ventanas de aviso:
- - 7 días antes: “Tu suscripción vence el {fecha}. Renueva para no perder acceso.”
- - 3 días antes: “Tu suscripción vence en 3 días.”
- - 1 día antes:  “Tu suscripción vence MAÑANA. Renueva ahora.”
- 
- Corre una vez al día (cada 24h). No envía duplicados porque solo busca
- suscripciones que vencen en una ventana estrecha (±12h del punto de aviso).
- 
- NOTA: Corre SIN TenantContext (operación de sistema).
 */
@Component
public class SubscriptionExpiryNotificationJob {

	private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryNotificationJob.class);
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final SubscriptionRepository subscriptionRepository;
	private final StaffUserRepository staffUserRepository;
	private final NotificationService notificationService;
	private final boolean enabled;

	public SubscriptionExpiryNotificationJob(
			SubscriptionRepository subscriptionRepository,
			StaffUserRepository staffUserRepository,
			NotificationService notificationService,
			@Value("${billing.expiry-notifications.enabled:true}") boolean enabled
			) {
		this.subscriptionRepository = subscriptionRepository;
		this.staffUserRepository = staffUserRepository;
		this.notificationService = notificationService;
		this.enabled = enabled;
	}

	/**
  - Ejecuta una vez al día. Busca suscripciones próximas a vencer.
	 */
	@Scheduled(fixedDelayString = "${billing.expiry-notifications.check-interval-ms:86400000}")
	@Transactional(readOnly = true)
	public void run() {
		if (!enabled) return;

		log.debug("Checking for expiring subscriptions…");

		Instant now = Instant.now();

		checkAndNotify(now, 7, "Tu suscripción vence el %s. Renueva para no perder acceso a Medica.");
		checkAndNotify(now, 3, "Tu suscripción vence en 3 días (%s). Renueva pronto.");
		checkAndNotify(now, 1, "¡Tu suscripción vence MAÑANA (%s)! Renueva ahora para no perder acceso.");
	}

	private void checkAndNotify(Instant now, int daysBeforeExpiry, String messageTemplate) {
		// Ventana: vencen entre (days - 0.5) y (days + 0.5) desde ahora
		Instant windowStart = now.plus(daysBeforeExpiry * 24 - 12, ChronoUnit.HOURS);
		Instant windowEnd = now.plus(daysBeforeExpiry * 24 + 12, ChronoUnit.HOURS);

		List<SubscriptionEntity> expiring =
				subscriptionRepository.findExpiringBetween(windowStart, windowEnd);

		if (!expiring.isEmpty()) {
			log.info("Found {} subscription(s) expiring in ~{} days", expiring.size(), daysBeforeExpiry);
		}

		for (SubscriptionEntity sub : expiring) {
			notifyTenantAdmins(sub, messageTemplate);
		}
	}

	private void notifyTenantAdmins(SubscriptionEntity sub, String messageTemplate) {
		String expiryDate = LocalDate.ofInstant(
				sub.getCurrentPeriodEnd(), ZoneId.systemDefault()).format(DATE_FORMAT);

		String message = String.format(messageTemplate, expiryDate);

		// Buscar ADMINs del tenant
		List<StaffUserEntity> admins = staffUserRepository.findByTenantId(sub.getTenantId())
				.stream()
				.filter(u -> "ADMIN".equals(u.getRole()) && u.isEnabled())
				.toList();

		for (StaffUserEntity admin : admins) {
			try {
				String subject = "Medica - Aviso de vencimiento de suscripción";

				if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
					notificationService.send(admin.getEmail(), subject, message);
				}

				log.debug("Expiry notification sent: tenant={}, admin={}",
						sub.getTenantId(), admin.getEmail());

			} catch (Exception e) {
				log.error("Failed to send expiry notification: tenant={}, admin={}, error={}",
						sub.getTenantId(), admin.getEmail(), e.getMessage());
			}
		}

	}
}