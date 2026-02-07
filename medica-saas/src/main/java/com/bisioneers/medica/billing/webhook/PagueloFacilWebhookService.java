package com.bisioneers.medica.billing.webhook;

import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PagueloFacilWebhookService {

    private final PaymentTransactionRepository txRepo;
    private final ObjectMapper mapper;

    public PagueloFacilWebhookService(PaymentTransactionRepository txRepo, ObjectMapper mapper) {
        this.txRepo = txRepo;
        this.mapper = mapper;
    }

    @Transactional
    public void process(String rawBody) {
        try {
            JsonNode json = mapper.readTree(rawBody);

            // Campos típicos según doc
            String codOper = json.path("codOper").asText(null);
            int status = json.path("status").asInt(-1); // 1 aprobada, 0 declinada
            String parm1 = json.path("PF_CF").asText(null); // a veces aquí, depende de integración
            // En muchos casos PARM_1 viene como parm1/parm_1 o dentro de "params". Hay que ajustarlo según tu payload real.

            // Recomendación: correlación por PARM_1 (transactionId).
            UUID txId = extractTransactionId(json);

            if (txId == null) {
                // Guarda auditoría y sal; sin correlación no actives nada.
                return;
            }

            PaymentTransactionEntity tx = txRepo.findById(txId).orElse(null);
            if (tx == null) return;

            // Idempotencia: si ya pagado, salir
            if ("PAID".equals(tx.getStatus())) return;

            // Guardar payload
            tx.setPayloadJson(append(tx.getPayloadJson(), rawBody));

            if (status == 1) {
                tx.setStatus("PAID");
                // TODO activar suscripción real
            } else if (status == 0) {
                tx.setStatus("DECLINED");
            }
            // codOper es útil como referencia del PSP (guárdalo si viene)
            if (codOper != null && !codOper.isBlank()) {
                tx.setProviderRef(codOper);
            }

            txRepo.save(tx);
        } catch (Exception e) {
            // En producción: log estructurado + métrica
        }
    }

    private UUID extractTransactionId(JsonNode json) {
        // Ajusta según el payload real que te llegue.
        // Priorizamos PARM_1 (transactionId)
        String[] possibleFields = {"PARM_1", "parm_1", "parm1", "custom_1", "PF_CF"};
        for (String f : possibleFields) {
            String val = json.path(f).asText(null);
            if (val != null && !val.isBlank()) {
                try { return UUID.fromString(val.trim()); } catch (Exception ignored) {}
            }
        }

        // Si viene anidado (ej: params.PARM_1)
        JsonNode params = json.path("params");
        if (params.isObject()) {
            String val = params.path("PARM_1").asText(null);
            if (val != null && !val.isBlank()) {
                try { return UUID.fromString(val.trim()); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String append(String existing, String next) {
        if (existing == null || existing.isBlank()) return next;
        return existing + "\n---\n" + next;
    }
}
