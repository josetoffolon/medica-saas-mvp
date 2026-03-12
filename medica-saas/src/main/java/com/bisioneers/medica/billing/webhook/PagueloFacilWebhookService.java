package com.bisioneers.medica.billing.webhook;

import com.bisioneers.medica.billing.BillingService;
import com.bisioneers.medica.billing.webhook.WebhookSecurityValidator.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**

- Procesa webhooks de Paguelo Fácil.
- 
- CAMBIOS vs versión anterior:
- - Inyecta WebhookSecurityValidator para validar IP y firma
- - process() ahora recibe HttpServletRequest para validación de seguridad
- - Logging estructurado en vez de catch silencioso
- - Retorna resultado para que el controller responda apropiadamente
- 
- Payload esperado de PF:
- {
- “Estado”: “Aprobada” | “Denegada”,
- “Oper”: “12345678”,
- “PARM_1”: “transaction-uuid”,
- “PARM_2”: “tenant-uuid”,
- …
- }
 */
@Service
public class PagueloFacilWebhookService {

	private static final Logger log = LoggerFactory.getLogger(PagueloFacilWebhookService.class);

	private final BillingService billingService;
	private final WebhookSecurityValidator securityValidator;
	private final ObjectMapper mapper;

	public PagueloFacilWebhookService(
			BillingService billingService,
			WebhookSecurityValidator securityValidator,
			ObjectMapper mapper
			) {
		this.billingService = billingService;
		this.securityValidator = securityValidator;
		this.mapper = mapper;
	}

	/**
  - Procesa un webhook de Paguelo Fácil.
  - 
  - @param request  el HttpServletRequest (para validación de IP/firma)
  - @param rawBody  el body crudo del webhook
  - @return resultado del procesamiento
	 */
	@Transactional
	public WebhookResult process(HttpServletRequest request, String rawBody) {

		// 1. Validar seguridad (IP + firma)
		ValidationResult validation = securityValidator.validate(request, rawBody);
		if (!validation.accepted()) {
			log.warn("Webhook rejected: {}", validation.reason());
			return WebhookResult.rejected(validation.reason());
		}

		// 2. Parsear payload
		try {
			JsonNode json = mapper.readTree(rawBody);

			String estado = json.path("Estado").asText(null);
			String oper = json.path("Oper").asText(null);
			String parm1 = json.path("PARM_1").asText(null);

			if (parm1 == null || parm1.isBlank()) {
				log.warn("Webhook ignored: missing PARM_1 (transactionId)");
				return WebhookResult.ignored("Missing PARM_1");
			}

			UUID txId;
			try {
				txId = UUID.fromString(parm1);
			} catch (IllegalArgumentException e) {
				log.warn("Webhook ignored: invalid PARM_1 format: {}", parm1);
				return WebhookResult.ignored("Invalid PARM_1 format");
			}

			log.info("Webhook received: estado={}, oper={}, txId={}", estado, oper, txId);

			// 3. Procesar según estado
			if ("Aprobada".equalsIgnoreCase(estado)) {
				billingService.markAsPaid(txId, oper, rawBody);
				log.info("Webhook processed: transaction {} marked as PAID", txId);
				return WebhookResult.processed("PAID");

			} else if ("Denegada".equalsIgnoreCase(estado)) {
				billingService.markAsDeclined(txId, oper, rawBody);
				log.info("Webhook processed: transaction {} marked as DECLINED", txId);
				return WebhookResult.processed("DECLINED");

			} else {
				log.warn("Webhook ignored: unknown estado '{}'", estado);
				return WebhookResult.ignored("Unknown estado: " + estado);
			}

		} catch (Exception e) {
			log.error("Webhook processing failed: {}", e.getMessage(), e);
			return WebhookResult.error(e.getMessage());
		}
	}

	// ─── Result ───────────────────────────────────────────────────────

	public record WebhookResult(String status, String detail) {
		public static WebhookResult processed(String detail) { return new WebhookResult("PROCESSED", detail); }
		public static WebhookResult rejected(String reason) { return new WebhookResult("REJECTED", reason); }
		public static WebhookResult ignored(String reason) { return new WebhookResult("IGNORED", reason); }
		public static WebhookResult error(String message) { return new WebhookResult("ERROR", message); }
		public boolean isRejected() { return "REJECTED".equals(status); }

	}
}