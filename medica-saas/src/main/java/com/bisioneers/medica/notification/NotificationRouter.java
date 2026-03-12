package com.bisioneers.medica.notification;

import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**

- Servicio principal de notificaciones que enruta al canal correcto.
- 
- Es el bean @Primary que el resto de la app inyecta cuando pide
- un NotificationService. Decide el canal según los datos del paciente:
- 
- 1. Si el paciente tiene teléfono Y WhatsApp está habilitado → WhatsApp
- 1. Si el paciente tiene email → Email
- 1. Fallback → Log (nunca falla)
- 
- Si el canal primario falla, intenta el siguiente en la cadena.
- 
- Para MVP esto cubre el caso común de clínicas en LATAM donde
- WhatsApp es el canal preferido para comunicación con pacientes.
 */
@Service
@Primary
public class NotificationRouter implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationRouter.class);

	private final NotificationService emailService;
	private final NotificationService whatsAppService;
	private final NotificationService logService;
	private final PatientRepository patientRepository;

	public NotificationRouter(
			@Qualifier("emailNotificationService") NotificationService emailService,
			@Qualifier("whatsAppNotificationService") NotificationService whatsAppService,
			@Qualifier("logNotificationService") NotificationService logService,
			PatientRepository patientRepository
			) {
		this.emailService = emailService;
		this.whatsAppService = whatsAppService;
		this.logService = logService;
		this.patientRepository = patientRepository;
	}

	@Override
	public boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message) {
		PatientEntity patient = patientRepository.findById(patientId).orElse(null);
		if (patient == null) {
			log.warn("Cannot send reminder: patient {} not found", patientId);
			return false;
		}

		boolean hasPhone = patient.getPhone() != null && !patient.getPhone().isBlank();
		boolean hasEmail = patient.getEmail() != null && !patient.getEmail().isBlank();
		boolean sent = false;

		// 1. Intentar WhatsApp (preferido en LATAM)
		if (hasPhone) {
			sent = whatsAppService.sendAppointmentReminder(
					tenantId, patientId, appointmentId, reminderType, message);
			if (sent) {
				log.debug("Reminder sent via WhatsApp: patient={}", patientId);
			}
		}

		// 2. Intentar Email (complementario o fallback)
		if (hasEmail) {
			boolean emailSent = emailService.sendAppointmentReminder(
					tenantId, patientId, appointmentId, reminderType, message);
			if (emailSent) {
				log.debug("Reminder sent via Email: patient={}", patientId);
				sent = true;
			}
		}

		// 3. Fallback a log si ningún canal funcionó
		if (!sent) {
			logService.sendAppointmentReminder(
					tenantId, patientId, appointmentId, reminderType, message);
			log.warn("Reminder only logged (no channel available): patient={}", patientId);
		}

		return sent;

	}

	@Override
	public boolean send(String to, String subject, String body) {
		// Para envíos directos, intentar determinar el canal por el formato del destino
		if (to != null && to.contains("@")) {
			return emailService.send(to, subject, body);
		} else if (to != null && (to.startsWith("+") || to.matches("\\d+"))) {
			return whatsAppService.send(to, subject, body);
		}
		return logService.send(to, subject, body);
	}

	@Override
	public String getChannel() {
		return "ROUTER";
	}
}