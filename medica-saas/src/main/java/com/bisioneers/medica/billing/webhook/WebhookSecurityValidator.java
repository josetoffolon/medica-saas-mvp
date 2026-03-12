package com.bisioneers.medica.billing.webhook;


import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**

- Validación de seguridad para webhooks de Paguelo Fácil.
- 
- Dos capas de protección:
- 
- 1. IP WHITELIST: Solo acepta requests desde IPs conocidas de PF.
- Configurable via paguelofacil.webhook.allowed-ips
- Si la lista está vacía, se desactiva la validación (solo dev).
- 
- 1. HMAC SIGNATURE (opcional): Si PF envía un header con firma HMAC,
- se valida contra el webhook secret configurado.
- Configurable via paguelofacil.webhook.secret
- Si el secret está vacío, se desactiva (para cuando PF no soporta firma).
- 
- Configuración en application.properties:
- 
- # IPs de producción de Paguelo Fácil (separadas por coma)
- paguelofacil.webhook.allowed-ips=200.46.148.0/24,200.46.149.0/24
- 
- # Secret para validación HMAC (si PF lo soporta)
- paguelofacil.webhook.secret=${PF_WEBHOOK_SECRET:}
- 
- # Header donde PF envía la firma (si aplica)
- paguelofacil.webhook.signature-header=X-PF-Signature
- 
- NOTA: Consultar con Paguelo Fácil sus IPs de producción exactas
- y si soportan firma HMAC en webhooks. Actualizar la configuración
- según la documentación vigente de PF.
 */
@Component
public class WebhookSecurityValidator {

	private static final Logger log = LoggerFactory.getLogger(WebhookSecurityValidator.class);

	private final Set<String> allowedIps;
	private final String webhookSecret;
	private final String signatureHeader;
	private final boolean ipValidationEnabled;
	private final boolean signatureValidationEnabled;

	public WebhookSecurityValidator(
			@Value("${paguelofacil.webhook.allowed-ips:}") String allowedIpsCsv,
			@Value("${paguelofacil.webhook.secret:}") String webhookSecret,
			@Value("${paguelofacil.webhook.signature-header:X-PF-Signature}") String signatureHeader
			) {
		this.allowedIps = parseIps(allowedIpsCsv);
		this.webhookSecret = webhookSecret;
		this.signatureHeader = signatureHeader;
		this.ipValidationEnabled = !this.allowedIps.isEmpty();
		this.signatureValidationEnabled = webhookSecret != null && !webhookSecret.isBlank();

		if (!ipValidationEnabled) {
			log.warn("Webhook IP validation DISABLED (paguelofacil.webhook.allowed-ips is empty). "
					+ "Configure IPs for production!");
		}
		if (!signatureValidationEnabled) {
			log.warn("Webhook signature validation DISABLED (paguelofacil.webhook.secret is empty).");
		}


	}

	/**
  - Valida que el request del webhook sea legítimo.
  - 
  - @param request  el HttpServletRequest del webhook
  - @param rawBody  el body crudo del webhook (para validar firma)
  - @return resultado de la validación
	 */
	public ValidationResult validate(HttpServletRequest request, String rawBody) {

		// 1. Validar IP
		if (ipValidationEnabled) {
			String clientIp = getClientIp(request);
			if (!isIpAllowed(clientIp)) {
				log.warn("Webhook rejected: unauthorized IP={}", clientIp);
				return ValidationResult.rejected("Unauthorized IP: " + clientIp);
			}
		}

		// 2. Validar firma HMAC (si está configurado)
		if (signatureValidationEnabled) {
			String signature = request.getHeader(signatureHeader);
			if (signature == null || signature.isBlank()) {
				log.warn("Webhook rejected: missing signature header {}", signatureHeader);
				return ValidationResult.rejected("Missing signature header");
			}

			String expectedSignature = computeHmacSha256(rawBody);
			if (!constantTimeEquals(signature, expectedSignature)) {
				log.warn("Webhook rejected: invalid signature");
				return ValidationResult.rejected("Invalid signature");
			}

		}

		return ValidationResult.accepted(true);
	}

	// ─── Helpers ──────────────────────────────────────────────────────

	/**
  - Obtiene la IP real del cliente, considerando proxies (X-Forwarded-For).
	 */
	private String getClientIp(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isBlank()) {
			// X-Forwarded-For puede tener múltiples IPs: “client, proxy1, proxy2”
			// La primera es la IP real del cliente
			return xForwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private boolean isIpAllowed(String clientIp) {
		if (clientIp == null) return false;
		// Match exacto o por prefijo (para soportar rangos tipo 200.46.148.*)
		return allowedIps.stream().anyMatch(allowed -> {
			if (allowed.endsWith("*")) {
				String prefix = allowed.substring(0, allowed.length() - 1);
				return clientIp.startsWith(prefix);
			}
			return allowed.equals(clientIp);
		});
	}

	private String computeHmacSha256(String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			SecretKeySpec key = new SecretKeySpec(
					webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			mac.init(key);
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			log.error("HMAC computation failed", e);
			return "";
		}
	}

	/**
  - Comparación en tiempo constante para prevenir timing attacks.
	 */
	private boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) return false;
		if (a.length() != b.length()) return false;
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}

	private Set<String> parseIps(String csv) {
		if (csv == null || csv.isBlank()) return Set.of();
		return List.of(csv.split(",")).stream()
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	// ─── Result ───────────────────────────────────────────────────────

	public record ValidationResult(boolean accepted, String reason) {
		public static ValidationResult accepted(boolean accepted) { return new ValidationResult(true, null); }
		public static ValidationResult rejected(String reason) { return new ValidationResult(false, reason); }
	}
}
