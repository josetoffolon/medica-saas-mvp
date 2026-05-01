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
 * entidad — se derivan o extraen del payloadJson:
 *   - description: derivado del status (ej: "Suscripción mensual")
 *   - paidAt: si status==PAID, se usa updatedAt; sino null
 *   - failedReason: si status==FAILED, se intenta extraer "Razon" del payloadJson
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

    public static TransactionDto from(PaymentTransactionEntity entity) {
        String status = entity.getStatus();
        Instant paidAt = "PAID".equals(status) ? entity.getUpdatedAt() : null;
        String failedReason = "FAILED".equals(status)
                ? extractFailedReason(entity.getPayloadJson())
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
     * Extrae el campo "Razon" o "razon" del payloadJson de Paguelo Fácil
     * cuando una transacción fue rechazada. Si el JSON no es parseable
     * o no contiene la razón, retorna null.
     */
    private static String extractFailedReason(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        try {
            JsonNode root = MAPPER.readTree(payloadJson);
            JsonNode razon = root.has("Razon") ? root.get("Razon") : root.get("razon");
            return razon != null && !razon.isNull() ? razon.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}