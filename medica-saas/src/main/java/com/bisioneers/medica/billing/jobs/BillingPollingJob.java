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

		// 1) Buscar PENDING recientes (límite para no cargar DB)
		List<PaymentTransactionEntity> pending = txRepo.findTop100ByStatusOrderByCreatedAtAsc("PENDING");

		Instant now = Instant.now();
		for (PaymentTransactionEntity tx : pending) {

			// Reconciliación: si tenemos codOper, preguntar a PF si ya se pagó.
			// markAsPaid hace la verificación server-to-server y activa si procede.
			if (tx.getProviderRef() != null && !tx.getProviderRef().isBlank()) {
				try {
					billingService.markAsPaid(tx.getId(), tx.getProviderRef(), null, null);
				} catch (Exception e) {
					log.warn("Reconciliación falló para tx {}: {}", tx.getId(), e.getMessage());
				}
				if (!"PENDING".equals(tx.getStatus())) continue; // ya resuelta
			}

			// Expirar si pasó el TTL del link + gracia
			Instant expiresAt = tx.getCreatedAt().plusSeconds(expiresInSeconds + graceSeconds);
			if (now.isAfter(expiresAt)) {
				tx.setStatus("EXPIRED");
				txRepo.save(tx);
			}

		}
	}
}
