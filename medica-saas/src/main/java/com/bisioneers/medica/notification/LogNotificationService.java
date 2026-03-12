package com.bisioneers.medica.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**

- Implementación fallback que solo loguea.
- 
- Usada cuando:
- - Email y WhatsApp no están configurados
- - Como fallback si ambos canales fallan
- - En desarrollo/testing
 */
@Service("logNotificationService")
public class LogNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(LogNotificationService.class);

	@Override
	public boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message) {
		log.info("[LOG] REMINDER [{}] tenant={}, patient={}, appointment={}: {}",
				reminderType, tenantId, patientId, appointmentId, message);
		return true;
	}

	@Override
	public boolean send(String to, String subject, String body) {
		log.info("[LOG] NOTIFICATION to={}, subject={}: {}", to, subject, body);
		return true;
	}

	@Override
	public String getChannel() {
		return "LOG";
	}
}