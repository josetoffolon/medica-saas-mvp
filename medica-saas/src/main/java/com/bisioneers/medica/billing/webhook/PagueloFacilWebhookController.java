package com.bisioneers.medica.billing.webhook;

import com.bisioneers.medica.billing.webhook.PagueloFacilWebhookService.WebhookResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**

- Controller para webhooks de Paguelo Fácil.
- 
- Ruta: POST /api/billing/webhook/paguelofacil
- (ya whitelisted en SecurityConfig y SubscriptionEnforcementFilter)
- 
- NOTA: Si ya existía un webhook controller, reemplazar con este.
- Este controller integra la validación de seguridad (IP + firma)
- via WebhookSecurityValidator.
- 
- Respuestas:
- 200 OK       → webhook procesado correctamente
- 200 OK       → webhook ignorado (PARM_1 faltante, estado desconocido)
- 403 Forbidden → IP no autorizada o firma inválida
- 500 Error    → error interno al procesar
- 
- IMPORTANTE: Siempre retornar 200 para webhooks ignorados.
- Si retornamos 4xx/5xx para payloads válidos pero ignorados,
- PF podría reintentar indefinidamente.
 */
@RestController
@RequestMapping("/api/billing/webhook")
public class PagueloFacilWebhookController {

	private static final Logger log = LoggerFactory.getLogger(PagueloFacilWebhookController.class);

	private final PagueloFacilWebhookService webhookService;

	public PagueloFacilWebhookController(PagueloFacilWebhookService webhookService) {
		this.webhookService = webhookService;
	}

	@PostMapping("/paguelofacil")
	public ResponseEntity<Map<String, String>> handleWebhook(
			HttpServletRequest request,
			@RequestBody String rawBody
			) {
		WebhookResult result = webhookService.process(request, rawBody);

		if (result.isRejected()) {
			// IP no autorizada o firma inválida → 403
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("status", "REJECTED"));
		}

		// Procesado, ignorado o error → siempre 200 para PF
		return ResponseEntity.ok(Map.of(
				"status", result.status(),
				"detail", result.detail() != null ? result.detail() : ""
				));

	}
}