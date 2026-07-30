package com.bisioneers.medica.billing.jobs;

import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.pf.PagueloFacilClient;
import com.bisioneers.medica.billing.BillingService;
import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class BillingPollingJob {

	private final boolean enabled;
	private final PaymentTransactionRepository txRepo;

	private final long expiresInSeconds;
	private final long graceSeconds;
	private final BillingService billingService;
	private static final Logger log = LoggerFactory.getLogger(BillingPollingJob.class);

	public BillingPollingJob(
			@Value("${billing.poll.enabled:true}") boolean enabled,
			PaymentTransactionRepository txRepo,
			BillingService billingService,
			@Value("${paguelofacil.expires-in:1800}") long expiresInSeconds,
			@Value("${billing.poll.expire-grace-seconds:120}") long graceSeconds
			) {
		this.enabled = enabled;
		this.txRepo = txRepo;
		this.billingService = billingService;
		this.expiresInSeconds = expiresInSeconds;
		this.graceSeconds = graceSeconds;
	}

	@Scheduled(fixedDelayString = "${billing.poll.fixed-delay-ms:300000}")
	@Transactional
	public void run() {
		if (!enabled) return;

		Instant now = Instant.now();

		// 1) PENDING recientes: PF puede haber cobrado sin que llegara el webhook
		List<PaymentTransactionEntity> pending =
				txRepo.findTop100ByStatusOrderByCreatedAtAsc("PENDING");

		for (PaymentTransactionEntity tx : pending) {
			reconcile(tx);

			if ("PENDING".equals(tx.getStatus())) {
				Instant expiresAt = tx.getCreatedAt().plusSeconds(expiresInSeconds + graceSeconds);
				if (now.isAfter(expiresAt)) {
					tx.setStatus("EXPIRED");
					txRepo.save(tx);
				}
			}
		}

		// 2) Transacciones cuyo webhook aprobó pero la verificación server-to-server
		//    falló por un corte transitorio hacia PF. El dinero pudo cobrarse, así
		//    que reintentamos la verificación. Si PF sigue sin confirmar, quedan
		//    en su estado y se reintentan el próximo ciclo (idempotente).
		for (String stuck : List.of("VERIFICATION_FAILED", "UNVERIFIED", "AMOUNT_UNVERIFIED")) {
			List<PaymentTransactionEntity> toRetry =
					txRepo.findTop100ByStatusOrderByCreatedAtAsc(stuck);
			for (PaymentTransactionEntity tx : toRetry) {
				// Solo reintentar dentro de una ventana razonable; más allá,
				// requiere revisión manual (evita reintentar indefinidamente).
				Instant giveUpAt = tx.getCreatedAt().plusSeconds(expiresInSeconds + graceSeconds + 86_400);
				if (now.isAfter(giveUpAt)) continue;
				reconcile(tx);
			}
		}
	}

	/**
	 * Intenta confirmar el pago contra PF. markAsPaid hace la verificación
	 * server-to-server y activa la suscripción solo si PF confirma; es
	 * idempotente, así que es seguro llamarlo en cada ciclo.
	 */
	private void reconcile(PaymentTransactionEntity tx) {
		if (tx.getProviderRef() == null || tx.getProviderRef().isBlank()) return;
		try {
			billingService.markAsPaid(tx.getId(), tx.getProviderRef(), null, null);
		} catch (Exception e) {
			log.warn("Reconciliación falló para tx {}: {}", tx.getId(), e.getMessage());
		}
	}
}
