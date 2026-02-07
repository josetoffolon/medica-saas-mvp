package com.bisioneers.medica.billing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bisioneers.medica.billing.domain.SubscriptionEntity;
import com.bisioneers.medica.billing.domain.SubscriptionRepository;
import com.bisioneers.medica.billing.pf.dto.SubscriptionStatusDto;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID tenantId) {
        return subscriptionRepository.findById(tenantId)
                .map(sub -> {
                    if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) return false;
                    Instant end = sub.getCurrentPeriodEnd();
                    return end != null && end.isAfter(Instant.now());
                })
                .orElse(false);
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

        // Si ya estaba activa y aún no vence, extiende desde el fin actual
        if ("ACTIVE".equalsIgnoreCase(sub.getStatus())
                && currentEnd != null
                && currentEnd.isAfter(now)) {
            start = currentEnd;
        }

        sub.setStatus("ACTIVE");
        sub.setCurrentPeriodStart(start);
        sub.setCurrentPeriodEnd(start.plus(1, ChronoUnit.MONTHS));

        // IMPORTANTE: tu entity define lastTransactionId como String o UUID?
        // Por tu error: setLastTransactionId(String) existe, así que guardamos el UUID como String:
        sub.setLastTransactionId(txId.toString());

        subscriptionRepository.save(sub);
    }
    
    @Transactional(readOnly = true)
    public SubscriptionStatusDto getStatus(UUID tenantId) {

        Instant now = Instant.now();

        return subscriptionRepository.findById(tenantId)
            .map(sub -> {
                String status = sub.getStatus();

                // si dice ACTIVE pero ya venció, lo tratamos como INACTIVE/PAST_DUE
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    Instant end = sub.getCurrentPeriodEnd();
                    if (end == null || !end.isAfter(now)) {
                        status = "PAST_DUE";
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
