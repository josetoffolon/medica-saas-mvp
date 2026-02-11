package com.bisioneers.medica.billing.webhook;

import com.bisioneers.medica.billing.BillingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PagueloFacilWebhookService {

  private final BillingService billingService;
  private final ObjectMapper mapper;

  public PagueloFacilWebhookService(BillingService billingService, ObjectMapper mapper) {
    this.billingService = billingService;
    this.mapper = mapper;
  }

  @Transactional
  public void process(String rawBody) {
    try {
      JsonNode json = mapper.readTree(rawBody);

      String estado = json.path("Estado").asText(null); // Aprobada/Denegada
      String oper   = json.path("Oper").asText(null);
      String parm1  = json.path("PARM_1").asText(null);

      if (parm1 == null) return;
      UUID txId = UUID.fromString(parm1);

      if ("Aprobada".equalsIgnoreCase(estado)) {
        billingService.markAsPaid(txId, oper, rawBody);
      } else if ("Denegada".equalsIgnoreCase(estado)) {
        billingService.markAsDeclined(txId, oper, rawBody);
      }

    } catch (Exception ignored) {
      // log
    }
  }
}
