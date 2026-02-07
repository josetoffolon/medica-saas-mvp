package com.bisioneers.medica.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.pf.PagueloFacilClient;
import com.bisioneers.medica.billing.pf.dto.CreateActivityRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BillingService_bk {

    private final PaymentTransactionRepository txRepo;
    private final PagueloFacilClient pfClient;
    private final ObjectMapper objectMapper;

    private final BigDecimal subscriptionAmount;
    private final String currency;
    private final int idPaymentStation;

    public BillingService_bk(
            PaymentTransactionRepository txRepo,
            PagueloFacilClient pfClient,
            ObjectMapper objectMapper,
            @Value("${billing.subscription-amount}") BigDecimal subscriptionAmount,
            @Value("${billing.currency:USD}") String currency,
            @Value("${paguelofacil.id-payment-station}") int idPaymentStation
    ) {
        this.txRepo = txRepo;
        this.pfClient = pfClient;
        this.objectMapper = objectMapper;
        this.subscriptionAmount = subscriptionAmount;
        this.currency = currency;
        this.idPaymentStation = idPaymentStation;
    }

    @Transactional
    public CheckoutResponse startCheckout(UUID tenantId, String tenantAlias) {
        // 1) Crear transacción interna
        PaymentTransactionEntity tx = new PaymentTransactionEntity();
        tx.setId(UUID.randomUUID());
        tx.setTenantId(tenantId);
        tx.setProvider("PAGUELO_FACIL");
        tx.setAmount(subscriptionAmount);
        tx.setCurrency(currency);
        tx.setStatus("PENDING");
        txRepo.save(tx);

        // 2) Crear Activity en PF
        String description = "Suscripción mensual - Médico " + tenantAlias + " - TX " + tx.getId();
        CreateActivityRequest req = new CreateActivityRequest(description, subscriptionAmount.doubleValue(), 0.0, idPaymentStation);
        JsonNode pfResp = pfClient.createActivity(req);
        System.out.println("PF createActivity response = " + pfResp.toPrettyString());
        
        if (pfResp.has("success") && !pfResp.path("success").asBoolean()) {
            String code = pfResp.path("headerStatus").path("code").asText("");
            String desc = pfResp.path("headerStatus").path("description").asText("");
            throw new IllegalStateException("PagueloFacil Activities rechazado. code=" + code + " desc=" + desc);
        }

        // 3) Guardar payload y providerRef=idActivity
        tx.setPayloadJson(pfResp.toString());

        Long idActivity = pfClient.tryExtractIdActivity(pfResp);
        if (idActivity == null) {
            // Fallo controlado: dejamos PENDING pero sin providerRef; mejor marcar FAILED en tu dominio.
            throw new IllegalStateException("No se pudo extraer idActivity de la respuesta de Paguelo Fácil.");
        }
        tx.setProviderRef(String.valueOf(idActivity));
        txRepo.save(tx);

        // 4) En este flujo no hay redirectUrl; el pago se completa en la estación/app PF.
        return new CheckoutResponse(tx.getId(), idActivity, "PENDING");
    }

    @Transactional
    public boolean confirmIfPaidByActivityId(String activityId) {
        // idempotencia
        PaymentTransactionEntity tx = txRepo.findByProviderRef(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada para activityId=" + activityId));

        if ("PAID".equals(tx.getStatus())) return true;

        // Consulta PF: status::2 significa pagada (según doc)
        String conditional = "status::2|idActivity::" + activityId;
        JsonNode queryResp = pfClient.queryActivities(conditional);

        // Guardar trazabilidad (append simple)
        tx.setPayloadJson(mergePayload(tx.getPayloadJson(), queryResp.toString()));
        txRepo.save(tx);

        boolean paid = pfClient.isPaidActivityQuery(queryResp);
        if (paid) {
            tx.setStatus("PAID");
            txRepo.save(tx);

            // TODO: activar suscripción (SubscriptionService)
            // subscriptionService.activate(tx.getTenantId(), Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));

            return true;
        }
        return false;
    }

    private String mergePayload(String existing, String newPiece) {
        if (existing == null || existing.isBlank()) return newPiece;
        return existing + "\n---\n" + newPiece;
    }

    public record CheckoutResponse(UUID transactionId, Long idActivity, String status) {}
}

