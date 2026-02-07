package com.bisioneers.medica.billing;

import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.pf.PagueloFacilLinkClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
            @Value("${app.return-url}") String returnUrl, SubscriptionService subscriptionService
    ) {
        this.txRepo = txRepo;
        this.linkClient = linkClient;
        this.subscriptionAmount = subscriptionAmount;
        this.currency = currency;
        this.returnUrl = returnUrl;
        this.subscriptionService = subscriptionService;
    }

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

        String desc = "Suscripción mensual - " + tenantAlias + " - " + tx.getId();

        String amt = subscriptionAmount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();

        var cmd = new PagueloFacilLinkClient.CreateLinkCommand(
                amt,
                desc,
                returnUrl,
                tx.getId().toString(),       // PARM_1 = transactionId
                tenantId.toString()          // PARM_2 = tenantId
        );

        var result = linkClient.createPaymentLink(cmd);

        // Guardar payload (y opcionalmente el code del link dentro del payload)
        tx.setPayloadJson(result.rawJson());

        // OJO: NO uses providerRef para el "code" si luego lo pisas con Oper.
        // Si quieres guardarlo, mételo en payload o crea columna linkCode.
        txRepo.save(tx);

        return new CheckoutResponse(tx.getId(), result.checkoutUrl(), "PENDING");
    }

    
    @Transactional
    public void markAsPaid(UUID transactionId, String providerRef, String rawPayload) {
        PaymentTransactionEntity tx = txRepo.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaccion no encontrada"));

        // Idempotencia fuerte
        if ("PAID".equals(tx.getStatus())) {
            return;
        }

        // Solo permitimos transición desde PENDING
        if (!"PENDING".equals(tx.getStatus())) {
            return;
        }

        tx.setStatus("PAID");
        tx.setProviderRef(providerRef);
        tx.setPayloadJson(rawPayload);

        txRepo.save(tx);

        // ACTIVAR SUSCRIPCIÓN
        subscriptionService.activateFromPaidTransaction(
                tx.getTenantId(),
                tx.getId()
        );
    }

    @Transactional
    public void markAsDeclined(UUID transactionId, String providerRef, String rawPayload) {
        PaymentTransactionEntity tx = txRepo.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaccion no encontrada"));

        if (!"PENDING".equals(tx.getStatus())) {
            return;
        }

        tx.setStatus("DECLINED");
        tx.setProviderRef(providerRef);
        tx.setPayloadJson(rawPayload);

        txRepo.save(tx);
    }
    public record CheckoutResponse(UUID transactionId, String redirectUrl, String status) {}
}
