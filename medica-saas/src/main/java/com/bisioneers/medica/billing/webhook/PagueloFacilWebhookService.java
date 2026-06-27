package com.bisioneers.medica.billing.webhook;

import com.bisioneers.medica.billing.BillingService;
import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.webhook.WebhookSecurityValidator.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
	private final PaymentTransactionRepository txRepo;

	private final BillingService billingService;
	private final WebhookSecurityValidator securityValidator;
	private final ObjectMapper mapper;

	public PagueloFacilWebhookService(
			PaymentTransactionRepository txRepo,
			BillingService billingService,
			WebhookSecurityValidator securityValidator,
			ObjectMapper mapper
			) {
		this.txRepo = txRepo;
		this.billingService = billingService;
		this.securityValidator = securityValidator;
		this.mapper = mapper;
	}

	@Transactional
	public WebhookResult process(HttpServletRequest request, String rawBody) {

	    ValidationResult validation = securityValidator.validate(request, rawBody);
	    if (!validation.accepted()) {
	        log.warn("Webhook rejected: {}", validation.reason());
	        return WebhookResult.rejected(validation.reason());
	    }

	    try {
	        JsonNode json = mapper.readTree(rawBody);

	        // Formato WEBHOOK real (doc PF): status 1/0, codOper, totalPay
	        int status = json.path("status").asInt(-1);
	        String codOper = json.path("codOper").asText(null);

	        // PARM_1 puede o no venir según configuración de PF.
	        String parm1 = json.path("PARM_1").asText(null);

	        // Correlación: preferimos PARM_1 (nuestro txId); si no viene,
	        // buscamos la transacción por codOper (guardado como providerRef).
	        PaymentTransactionEntity tx = resolveTransaction(parm1, codOper);
	        if (tx == null) {
	            log.warn("Webhook ignored: no se pudo correlacionar (parm1={}, codOper={})",
	                    parm1, codOper);
	            return WebhookResult.ignored("Transaction not found");
	        }

	        log.info("Webhook received: status={}, codOper={}, txId={}", status, codOper, tx.getId());

	        if (status == 1) {
	            BigDecimal paidAmount = extractAmount(json);
	            billingService.markAsPaid(tx.getId(), codOper, paidAmount, rawBody);
	            return WebhookResult.processed("PAID");
	        } else if (status == 0) {
	            billingService.markAsDeclined(tx.getId(), codOper, rawBody);
	            return WebhookResult.processed("DECLINED");
	        } else {
	            log.warn("Webhook ignored: status desconocido '{}'", status);
	            return WebhookResult.ignored("Unknown status: " + status);
	        }

	    } catch (Exception e) {
	        log.error("Webhook processing failed: {}", e.getMessage(), e);
	        return WebhookResult.error(e.getMessage());
	    }
	}

	private PaymentTransactionEntity resolveTransaction(String parm1, String codOper) {
	    if (parm1 != null && !parm1.isBlank()) {
	        try {
	            return txRepo.findById(UUID.fromString(parm1)).orElse(null);
	        } catch (IllegalArgumentException ignored) { }
	    }
	    if (codOper != null && !codOper.isBlank()) {
	        return txRepo.findByProviderRef(codOper).orElse(null);
	    }
	    return null;
	}

	/**
	 * Extrae el monto pagado del payload del WEBHOOK de PF.
	 *
	 * Según doc oficial (developers.paguelofacil.com/guias/enlace-de-pago),
	 * el webhook envía:
	 *   - totalPay         → monto total de la transacción
	 *   - requestPayAmount → monto solicitado en la petición
	 * Comparamos contra totalPay (el efectivamente cobrado).
	 */
	private BigDecimal extractAmount(JsonNode json) {
	    String[] candidates = {"totalPay", "requestPayAmount"};
	    for (String field : candidates) {
	        JsonNode n = json.get(field);
	        if (n != null && !n.isNull()) {
	            try {
	                return new BigDecimal(n.asText().trim());
	            } catch (NumberFormatException ignored) { }
	        }
	    }
	    return null;
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