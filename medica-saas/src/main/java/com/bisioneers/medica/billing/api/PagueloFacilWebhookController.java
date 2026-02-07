package com.bisioneers.medica.billing.api;

import com.bisioneers.medica.billing.webhook.PagueloFacilWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/webhook")
public class PagueloFacilWebhookController {

    private final PagueloFacilWebhookService webhookService;

    public PagueloFacilWebhookController(PagueloFacilWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/paguelofacil")
    public ResponseEntity<?> receive(@RequestBody String rawBody) {
    	System.out.println("WEBHOOK PF RAW: " + rawBody);
        webhookService.process(rawBody);
        // Responder 200 rápido (PF puede reintentar)
        return ResponseEntity.ok().build();
    }
    
	/*Conectar WEBHOOK (cuando tengas VPS)
	 * 
	 * @PostMapping("/paguelofacil") public ResponseEntity<Void>
	 * receive(@RequestBody String rawBody) {
	 * 
	 * JsonNode json = mapper.readTree(rawBody);
	 * 
	 * UUID txId = UUID.fromString(json.path("PARM_1").asText()); String estado =
	 * json.path("Estado").asText(); String oper = json.path("Oper").asText();
	 * 
	 * if ("Aprobada".equalsIgnoreCase(estado)) { billingService.markAsPaid(txId,
	 * oper, rawBody); } else if ("Denegada".equalsIgnoreCase(estado)) {
	 * billingService.markAsDeclined(txId, oper, rawBody); }
	 * 
	 * return ResponseEntity.ok().build(); }
	 */

}
