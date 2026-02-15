package com.bisioneers.medica.billing.jobs;

import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;

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

    public BillingPollingJob(
            @Value("${billing.poll.enabled:true}") boolean enabled,
            PaymentTransactionRepository txRepo,
            @Value("${paguelofacil.expires-in:1800}") long expiresInSeconds,
            @Value("${billing.poll.expire-grace-seconds:120}") long graceSeconds
    ) {
        this.enabled = enabled;
        this.txRepo = txRepo;
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

            // 2) Si ya llegó codOper por webhook y aún está PENDING,
            // aquí podrías consultar PF para confirmar (cuando tengamos endpoint de consulta).
            // Por ahora, solo dejamos que webhook haga su trabajo.
            // if (tx.getProviderRef() != null) { ...consulta... }

            // 3) Expirar si ya pasó el TTL del link + gracia
            Instant expiresAt = tx.getCreatedAt()
                    .plusSeconds(expiresInSeconds + graceSeconds);

            if (now.isAfter(expiresAt)) {
                tx.setStatus("EXPIRED");
                txRepo.save(tx);
            }
            
            if (!"PENDING".equals(tx.getStatus())) {
                continue;
            }

        }
    }
}
