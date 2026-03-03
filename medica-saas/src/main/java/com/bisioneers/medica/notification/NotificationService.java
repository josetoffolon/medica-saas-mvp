package com.bisioneers.medica.notification;

import java.util.UUID;

/**

- Interfaz de notificaciones.
- 
- MVP: LogNotificationService (solo loguea, no envía realmente)
- Fase 2: EmailNotificationService (SMTP) + WhatsAppNotificationService (Twilio)
- 
- Cada implementación puede coexistir — el job de recordatorios
- inyecta la interfaz y la implementación activa se encarga del envío.
 */
public interface NotificationService {

	/**
  - Envía un recordatorio de cita al paciente.
  - 
  - @param tenantId      ID del tenant
  - @param patientId     ID del paciente
  - @param appointmentId ID de la cita
  - @param reminderType  “24h” o “2h”
  - @param message       mensaje del recordatorio
  - @return true si se envió correctamente
	 */
	boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message);
}