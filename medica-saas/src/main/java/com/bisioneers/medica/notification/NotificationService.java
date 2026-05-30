package com.bisioneers.medica.notification;

import java.util.UUID;

/**

- Interfaz de envío de notificaciones.
- 
- Implementaciones:
- - EmailNotificationService  → SMTP (Spring Mail)
- - WhatsAppNotificationService → Twilio WhatsApp API
- - LogNotificationService    → Solo loguea (fallback / dev)
- 
- La selección de canal se hace en NotificationRouter,
- que decide a cuál implementación delegar según los datos del paciente.
- 
- NOTA: Esta interfaz reemplaza la versión anterior (que solo tenía
- sendAppointmentReminder). Los métodos ahora son más granulares.
 */
public interface NotificationService {

	/**
  - Envía un recordatorio de cita.
  - 
  - @return true si se envió correctamente
	 */
	boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message);

	/**
  - Envía una notificación genérica a un destinatario.
  - 
  - @param to       email o número de teléfono (según implementación)
  - @param subject  asunto (solo email, WhatsApp lo ignora)
  - @param body     contenido del mensaje
  - @return true si se envió correctamente
	 */
	boolean send(String to, String subject, String body);

	/**
  - Identificador del canal (para logging y routing).
  - Ej: “EMAIL”, “WHATSAPP”, “LOG”
	 */
	String getChannel();
	
}