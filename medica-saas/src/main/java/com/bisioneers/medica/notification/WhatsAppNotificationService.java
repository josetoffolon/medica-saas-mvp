package com.bisioneers.medica.notification;

import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**

- Envío de notificaciones por WhatsApp via Twilio API.
- 
- Usa la API REST de Twilio directamente (sin SDK) para minimizar dependencias.
- 
- Configuración en application.properties:
- twilio.account-sid=${TWILIO_ACCOUNT_SID}
- twilio.auth-token=${TWILIO_AUTH_TOKEN}
- twilio.whatsapp-from=${TWILIO_WHATSAPP_NUMBER}
- 
- REQUISITOS:
- - Cuenta Twilio con WhatsApp Sandbox habilitado (dev) o número aprobado (prod)
- - El paciente debe tener phone con código de país (ej: +5076001234)
- - El número debe estar registrado en el sandbox de Twilio (dev)
- 
- FORMATO DE NÚMERO:
- La API de Twilio espera “whatsapp:+XXXXXXXXXXX”.
- El servicio normaliza el teléfono del paciente automáticamente.
 */
@Service("whatsAppNotificationService")
public class WhatsAppNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

	private final PatientRepository patientRepository;
	private final RestTemplate restTemplate;
	private final String accountSid;
	private final String authToken;
	private final String fromNumber;
	private final boolean enabled;

	public WhatsAppNotificationService(
			PatientRepository patientRepository,
			@Value("${twilio.account-sid:}") String accountSid,
			@Value("${twilio.auth-token:}") String authToken,
			@Value("${twilio.whatsapp-from:}") String fromNumber
			) {
		this.patientRepository = patientRepository;
		this.restTemplate = new RestTemplate();
		this.accountSid = accountSid;
		this.authToken = authToken;
		this.fromNumber = fromNumber;
		this.enabled = accountSid != null && !accountSid.isBlank()
				&& authToken != null && !authToken.isBlank();
		if (!enabled) {
			log.warn("WhatsApp notifications DISABLED (twilio credentials not configured)");
		}
	}

	@Override
	public boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message) {
		if (!enabled) return false;

		PatientEntity patient = patientRepository.findById(patientId).orElse(null);
		if (patient == null || patient.getPhone() == null || patient.getPhone().isBlank()) {
			log.debug("WhatsApp reminder skipped: no phone for patient {}", patientId);
			return false;
		}
		return send(patient.getPhone(), null, message);

	}

	@Override
	public boolean send(String to, String subject, String body) {
		if (!enabled) {
			log.debug("WhatsApp disabled, skipping message to {}", to);
			return false;
		}
		try {
			String normalizedTo = normalizeWhatsAppNumber(to);
			String normalizedFrom = normalizeWhatsAppNumber(fromNumber);

			String url = String.format(
					"https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json",
					accountSid);

			// Basic Auth header
			String credentials = accountSid + ":" + authToken;
			String encodedAuth = Base64.getEncoder()
					.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			headers.set("Authorization", "Basic " + encodedAuth);

			// Form body
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("From", normalizedFrom);
			form.add("To", normalizedTo);
			form.add("Body", body);

			HttpEntity<MultiValueMap<String, String>> request =
					new HttpEntity<>(form, headers);

			ResponseEntity<String> response = restTemplate.exchange(
					url, HttpMethod.POST, request, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				log.info("WhatsApp sent: to={}", to);
				return true;
			} else {
				log.warn("WhatsApp failed: to={}, status={}", to, response.getStatusCode());
				return false;
			}

		} catch (Exception e) {
			log.error("WhatsApp error: to={}, error={}", to, e.getMessage());
			return false;
		}
	}

	@Override
	public String getChannel() {
		return "WHATSAPP";
	}

	/**
  - Normaliza un número de teléfono al formato de WhatsApp Twilio.
  - 
  - Entrada:  “+5076001234”, “6001234”, “507-6001-234”
  - Salida:   “whatsapp:+5076001234”
	 */
	private String normalizeWhatsAppNumber(String phone) {
		if (phone == null) return "";

		// Remover whatsapp: si ya lo tiene
		String clean = phone.replace("whatsapp:", "").trim();

		// Remover guiones, espacios, paréntesis
		clean = clean.replaceAll("[\\s\\-()]", "");

		// Agregar + si no lo tiene
		if (!clean.startsWith("+")) {
			clean = "+" + clean;
		}
		return "whatsapp:" + clean;
	}

}
