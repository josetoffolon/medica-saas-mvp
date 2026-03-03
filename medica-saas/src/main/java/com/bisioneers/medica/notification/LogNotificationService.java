package com.bisioneers.medica.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**

- Implementación MVP que solo loguea los recordatorios.
- 
- Para producción, reemplazar con EmailNotificationService y/o
- WhatsAppNotificationService que implementen la misma interfaz.
- 
- Las properties de Twilio y SMTP ya existen en application.properties:
- spring.mail.host, spring.mail.port, etc.
- twilio.account-sid, twilio.auth-token, twilio.whatsapp-from
 */
@Service
public class LogNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(LogNotificationService.class);

	@Override
	public boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message) {
		log.info("REMINDER [{}] tenant={}, patient={}, appointment={}: {}",
				reminderType, tenantId, patientId, appointmentId, message);

		// MVP: siempre retorna true (simulando envío exitoso)
		// En producción, la implementación real retornaría false si el envío falla
		return true;
	}
}
