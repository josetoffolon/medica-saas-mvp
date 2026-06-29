package com.bisioneers.medica.billing.dto;

import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de transacción para exponer al frontend.
 *
 * Coincide con la interface Transaction del frontend
 * (src/app/features/billing/services/billing.service.ts).
 *
 * Algunos campos que el frontend espera no existen como columnas en la
 * entidad — se derivan:
 *   - description: derivado del status (ej: "Suscripción mensual")
 *   - paidAt: si status==PAID, se usa updatedAt; sino null
 *   - failedReason: si la transacción fue rechazada (DECLINED/FAILED), se
 *     extrae "Razon" del ÚLTIMO payment_event de la transacción.
 *
 * #16: la razón ya NO se lee del payloadJson concatenado (eliminado).
 * El controller resuelve el último payment_event y lo pasa como lastEventJson,
 * porque este método es estático y no tiene acceso al repositorio.
 */
public record TransactionDto(
        UUID id,
        BigDecimal amount,
        String currency,
        String status,
        String paymentProvider,
        String providerTransactionId,
        String description,
        Instant createdAt,
        Instant paidAt,
        String failedReason
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param entity        la transacción
     * @param lastEventJson rawJson del último payment_event (puede ser null)
     */
    public static TransactionDto from(PaymentTransactionEntity entity, String lastEventJson) {
        String status = entity.getStatus();
        Instant paidAt = "PAID".equals(status) ? entity.getUpdatedAt() : null;

        // Los rechazos del webhook se marcan como DECLINED; FAILED se mantiene
        // por compatibilidad con estados antiguos.
        String failedReason = ("DECLINED".equals(status) || "FAILED".equals(status))
                ? extractFailedReason(lastEventJson)
                : null;

        return new TransactionDto(
                entity.getId(),
                entity.getAmount(),
                entity.getCurrency(),
                status,
                entity.getProvider(),
                entity.getProviderRef(),
                "Suscripción mensual",
                entity.getCreatedAt(),
                paidAt,
                failedReason
        );
    }

    /**
     * Extrae el campo "Razon"/"razon" del payload de Paguelo Fácil cuando una
     * transacción fue rechazada. Si el JSON es null, no parseable o no contiene
     * la razón, retorna null.
     */
    private static String extractFailedReason(String eventJson) {
        if (eventJson == null || eventJson.isBlank()) return null;
        try {
            JsonNode root = MAPPER.readTree(eventJson);
            JsonNode razon = root.has("Razon") ? root.get("Razon") : root.get("razon");
            return razon != null && !razon.isNull() ? razon.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}