package com.bisioneers.medica.billing;

import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.pf.PagueloFacilLinkClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class BillingService {

    private final PaymentTransactionRepository txRepo;
    private final PagueloFacilLinkClient linkClient;
    private final SubscriptionService subscriptionService;

    private final BigDecimal subscriptionAmount;
    private final String currency;
    private final String returnUrl;

    public BillingService(
            PaymentTransactionRepository txRepo,
            PagueloFacilLinkClient linkClient,
            @Value("${billing.subscription-amount}") BigDecimal subscriptionAmount,
            @Value("${billing.currency:USD}") String currency,
            @Value("${app.return-url}") String returnUrl,
            SubscriptionService subscriptionService
    ) {
        this.txRepo = txRepo;
        this.linkClient = linkClient;
        this.subscriptionAmount = subscriptionAmount;
        this.currency = currency;
        this.returnUrl = returnUrl;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Crea transacción PENDING y genera link de pago en PF.
     * - Guarda payload sin pisar (append)
     * - Si PF falla: marca ERROR + registra error en payload y relanza excepción
     */
    @Transactional
    public CheckoutResponse startCheckout(UUID tenantId, String tenantAlias) {

        PaymentTransactionEntity tx = new PaymentTransactionEntity();
        tx.setId(UUID.randomUUID());
        tx.setTenantId(tenantId);
        tx.setProvider("PAGUELO_FACIL_LINK");
        tx.setAmount(subscriptionAmount);
        tx.setCurrency(currency);
        tx.setStatus("PENDING");
        txRepo.save(tx);

        String desc = "Suscripción mensual - " + (tenantAlias == null ? "" : tenantAlias) + " - " + tx.getId();
        String amt = subscriptionAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();

        var cmd = new PagueloFacilLinkClient.CreateLinkCommand(
                amt,
                desc,
                returnUrl,
                tx.getId().toString(), // PARM_1 = transactionId
                tenantId.toString()    // PARM_2 = tenantId
        );

        try {
            var result = linkClient.createPaymentLink(cmd);

            // Guardar respuesta PF como auditoría
            tx.setPayloadJson(appendPayload(tx.getPayloadJson(), result.rawJson()));

            // Si algún día guardas "code", NO lo metas en providerRef (providerRef se usa para "Oper" del webhook)
            // tx.setPayloadJson(appendPayload(tx.getPayloadJson(), "{\"pfLinkCode\":\"" + safe(result.code()) + "\"}"));

            txRepo.save(tx);

            return new CheckoutResponse(tx.getId(), result.checkoutUrl(), tx.getStatus());
        } catch (Exception ex) {
            tx.setStatus("ERROR");
            tx.setPayloadJson(appendPayload(tx.getPayloadJson(),
                    "{\"time\":\"" + Instant.now() + "\",\"error\":\"PF_LINK_CREATE_FAILED\",\"message\":\"" + safe(ex.getMessage()) + "\"}"
            ));
            txRepo.save(tx);
            throw ex;
        }
    }

    /**
     * Webhook PF: transacción aprobada.
     * - Idempotente (si ya está PAID no repite)
     * - Guarda providerRef (Oper) y append de payload
     * - Activa suscripción desde tx pagada
     */
    @Transactional
    public void markAsPaid(UUID txId, String providerRef, String rawBody) {

        PaymentTransactionEntity tx = txRepo.findById(txId)
                .orElseThrow(() -> new IllegalStateException("Transaccion no encontrada: " + txId));

        if ("PAID".equalsIgnoreCase(tx.getStatus())) {
            // Igual guardamos payload si viene nuevo? (opcional)
            if (rawBody != null && !rawBody.isBlank()) {
                tx.setPayloadJson(appendPayload(tx.getPayloadJson(), rawBody));
                if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
                txRepo.save(tx);
            }
            return;
        }

        tx.setStatus("PAID");
        if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
        tx.setPayloadJson(appendPayload(tx.getPayloadJson(), rawBody));

        txRepo.save(tx);

        // Cierre real de Billing: activa o extiende suscripción
        subscriptionService.activateFromPaidTransaction(tx.getTenantId(), tx.getId());
    }

    /**
     * Webhook PF: transacción declinada.
     * - Idempotente: si ya está PAID no se toca; si ya está DECLINED tampoco.
     * - Guarda providerRef y append payload
     */
    @Transactional
    public void markAsDeclined(UUID txId, String providerRef, String rawBody) {

        PaymentTransactionEntity tx = txRepo.findById(txId)
                .orElseThrow(() -> new IllegalStateException("Transaccion no encontrada: " + txId));

        if ("PAID".equalsIgnoreCase(tx.getStatus())) {
            // no degradamos a DECLINED
            tx.setPayloadJson(appendPayload(tx.getPayloadJson(), rawBody));
            txRepo.save(tx);
            return;
        }

        if ("DECLINED".equalsIgnoreCase(tx.getStatus())) {
            tx.setPayloadJson(appendPayload(tx.getPayloadJson(), rawBody));
            if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
            txRepo.save(tx);
            return;
        }

        tx.setStatus("DECLINED");
        if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
        tx.setPayloadJson(appendPayload(tx.getPayloadJson(), rawBody));

        txRepo.save(tx);
    }

    public record CheckoutResponse(UUID transactionId, String redirectUrl, String status) {}

    // ---------------- helpers ----------------

    private String appendPayload(String existing, String next) {
        if (next == null || next.isBlank()) return existing;
        if (existing == null || existing.isBlank()) return next;
        return existing + "\n---\n" + next;
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", " ").replace("\r", " ");
    }
}