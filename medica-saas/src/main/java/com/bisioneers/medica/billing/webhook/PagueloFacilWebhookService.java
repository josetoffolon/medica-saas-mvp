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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procesa webhooks de Paguelo Facil.
 *
 * Payload REAL del webhook (verificado en sandbox 2026-07-23):
 * {
 *   "status": 0,                                  // 1 = aprobada, 0 = denegada
 *   "codOper": "SANDBOX_LK-DTRV4OE2VBRI",         // codigo de operacion de PF
 *   "messageSys": "DECLINE",
 *   "authStatus": "51",
 *   "totalPay": "100.0",                          // string, escala variable
 *   "requestPayAmount": 100,
 *   "description": "Suscripcion mensual - {alias} - {txId}",
 *   "returnUrl": "http://localhost:4200/billing/return?PARM_1={txId}&PARM_2={tenantId}",
 *   "inRevision": false,                          // true = retenida por antifraude
 *   "binInfo": { "risk_score": 70, ... }
 * }
 *
 * IMPORTANTE — correlacion:
 * PF NO envia PARM_1 como campo de primer nivel. Si eco el returnUrl
 * completo (que lo contiene) y una description que embebe el txId, asi que
 * la correlacion se resuelve parseando el propio payload, sin consultas
 * externas. El codigo que devuelve LinkDeamon al crear el enlace
 * (data.code) NO coincide con el codOper del webhook, por lo que no sirve
 * para correlacionar.
 *
 * Correlacionar no es autorizar: la activacion sigue dependiendo de la
 * verificacion server-to-server dentro de BillingService.markAsPaid.
 */
@Service
public class PagueloFacilWebhookService {

	private static final Logger log = LoggerFactory.getLogger(PagueloFacilWebhookService.class);

	private static final Pattern UUID_PATTERN = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

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

			int status = json.path("status").asInt(-1);
			String codOper = json.path("codOper").asText(null);

			PaymentTransactionEntity tx = resolveTransaction(json);
			if (tx == null) {
				log.warn("Webhook ignored: no se pudo correlacionar (codOper={}, returnUrl={})",
						codOper, json.path("returnUrl").asText(null));
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

	// ─── Correlacion ──────────────────────────────────────────────────

	/**
	 * Correlaciona el webhook con la transaccion local, en 4 etapas.
	 * Devuelve null si ninguna vía resuelve.
	 *
	 * No persiste el codOper: markAsPaid y markAsDeclined ya hacen
	 * setProviderRef(providerRef) con ese mismo valor.
	 */
	private PaymentTransactionEntity resolveTransaction(JsonNode json) {

		// 1) PARM_1 de primer nivel (por si PF lo habilita en el futuro)
		PaymentTransactionEntity byParm1 = findByTxIdString(json.path("PARM_1").asText(null));
		if (byParm1 != null) {
			return byParm1;
		}

		// 2) PARM_1 dentro del returnUrl que PF eco — vía principal hoy
		String returnUrl = json.path("returnUrl").asText(null);
		Optional<String> parm1FromUrl = extractQueryParam(returnUrl, "PARM_1");
		if (parm1FromUrl.isPresent()) {
			PaymentTransactionEntity tx = findByTxIdString(parm1FromUrl.get());
			if (tx != null) {
				log.debug("Correlacion por returnUrl: txId={}", tx.getId());
				return tx;
			}
		}

		// 3) codOper ya correlacionado (webhook duplicado / reintento de PF)
		String codOper = json.path("codOper").asText(null);
		if (codOper != null && !codOper.isBlank()) {
			PaymentTransactionEntity byRef = txRepo.findByProviderRef(codOper.trim()).orElse(null);
			if (byRef != null) {
				return byRef;
			}
		}

		// 4) Fallback: txId embebido en la description del cobro
		String description = json.path("description").asText(null);
		if (description != null && !description.isBlank()) {
			Matcher m = UUID_PATTERN.matcher(description);
			if (m.find()) {
				PaymentTransactionEntity tx = findByTxIdString(m.group());
				if (tx != null) {
					log.info("Correlacion por description: txId={}, codOper={}", tx.getId(), codOper);
					return tx;
				}
			}
		}

		return null;
	}

	private PaymentTransactionEntity findByTxIdString(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return txRepo.findById(UUID.fromString(raw.trim())).orElse(null);
		} catch (IllegalArgumentException e) {
			log.warn("Valor no es un UUID valido: {}", raw);
			return null;
		}
	}

	/**
	 * Extrae un query param de una URL. Se parsea el query string en vez de
	 * buscar el primer UUID de la cadena, para no confundir PARM_1 (txId)
	 * con PARM_2 (tenantId).
	 */
	private static Optional<String> extractQueryParam(String url, String paramName) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		int q = url.indexOf('?');
		if (q < 0 || q == url.length() - 1) {
			return Optional.empty();
		}
		for (String pair : url.substring(q + 1).split("&")) {
			int eq = pair.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			try {
				String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
				if (paramName.equals(key)) {
					String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
					return value.isBlank() ? Optional.empty() : Optional.of(value);
				}
			} catch (IllegalArgumentException ignored) {
				// secuencia de escape invalida: seguir con el siguiente par
			}
		}
		return Optional.empty();
	}

	// ─── Monto ────────────────────────────────────────────────────────

	/**
	 * Extrae el monto pagado del payload del WEBHOOK de PF.
	 * Campos reales: totalPay (efectivamente cobrado) y requestPayAmount
	 * (solicitado). Llega como string con escala variable ("100.0"), por lo
	 * que markAsPaid debe comparar con compareTo, no equals.
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