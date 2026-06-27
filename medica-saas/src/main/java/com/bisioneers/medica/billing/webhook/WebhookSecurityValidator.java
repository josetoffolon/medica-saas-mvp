package com.bisioneers.medica.billing.webhook;


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import java.util.Arrays;
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
	private final List<IpAddressMatcher> allowedIpMatchers;


	private final String webhookSecret;
	private final String signatureHeader;
	private final boolean ipValidationEnabled;
	private final boolean signatureValidationEnabled;

	public WebhookSecurityValidator(
			@Value("${paguelofacil.webhook.allowed-ips:}") String allowedIpsCsv,
			@Value("${paguelofacil.webhook.secret:}") String webhookSecret,
			@Value("${paguelofacil.webhook.signature-header:X-PF-Signature}") String signatureHeader
			) {
		this.allowedIpMatchers = parseIps(allowedIpsCsv);
		this.webhookSecret = webhookSecret;
		this.signatureHeader = signatureHeader;
		this.ipValidationEnabled = !this.allowedIpMatchers.isEmpty();
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
	 * IP real del cliente.
	 *
	 * Con server.forward-headers-strategy=framework (ya configurado),
	 * getRemoteAddr() ya refleja la IP resuelta por Spring. NO parseamos
	 * X-Forwarded-For manualmente porque es spoofeable si la app es alcanzable
	 * de forma directa.
	 *
	 * La frontera de seguridad REAL es doble:
	 *   (1) que solo el proxy confiable (Nginx) pueda alcanzar el puerto de la app, y
	 *   (2) la firma HMAC del webhook (defensa en profundidad, abajo).
	 * El whitelist de IP es un filtro grueso, no el control fuerte.
	 */
	private String getClientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	private boolean isIpAllowed(String clientIp) {
		if (clientIp == null) return false;
		return allowedIpMatchers.stream().anyMatch(m -> {
			try {
				return m.matches(clientIp);
			} catch (IllegalArgumentException e) {
				return false; // IP malformada
			}
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

	private List<IpAddressMatcher> parseIps(String csv) {
		if (csv == null || csv.isBlank()) return List.of();
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(IpAddressMatcher::new) // soporta IP exacta y CIDR: 200.46.148.0/24
				.toList();
	}

	// ─── Result ───────────────────────────────────────────────────────

	public record ValidationResult(boolean accepted, String reason) {
		public static ValidationResult accepted(boolean accepted) { return new ValidationResult(true, null); }
		public static ValidationResult rejected(String reason) { return new ValidationResult(false, reason); }
	}
}
