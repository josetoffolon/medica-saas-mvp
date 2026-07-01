package com.bisioneers.medica.appointment.jobs;

import com.bisioneers.medica.appointment.domain.AppointmentEntity;
import com.bisioneers.medica.appointment.domain.AppointmentRepository;
import com.bisioneers.medica.notification.NotificationService;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**

- Job programado que envía recordatorios de citas.
- 
- Dos tipos de recordatorio:
- - 24h antes: “Tienes una cita mañana a las 10:00”
- - 2h antes:  “Tu cita es en 2 horas (10:00)”
- 
- El job corre cada 10 minutos y busca citas que:
- 1. Tengan status SCHEDULED o CONFIRMED
- 1. No hayan recibido el recordatorio correspondiente
- 1. Estén dentro de la ventana de tiempo
- 
- Ventanas de búsqueda:
- - 24h: citas entre ahora+23h y ahora+25h (para capturar citas en ±1h de las 24h)
- - 2h:  citas entre ahora+1h30m y ahora+2h30m
- 
- El envío real se delega a NotificationService.
- MVP usa LogNotificationService (solo loguea).
- 
- NOTA: Este job corre SIN TenantContext (es una operación de sistema).
- Las queries del repository usan filtros explícitos, no el Hibernate filter.
- El TenantAwareTransactionManager NO activa el filtro cuando tenantId es null.
 */
@Component
public class AppointmentReminderJob {

	private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final AppointmentRepository appointmentRepository;
	private final PatientRepository patientRepository;
	private final NotificationService notificationService;
	private final boolean enabled;

	public AppointmentReminderJob(
			AppointmentRepository appointmentRepository,
			PatientRepository patientRepository,
			NotificationService notificationService,
			@Value("${appointment.reminders.enabled:true}") boolean enabled
			) {
		this.appointmentRepository = appointmentRepository;
		this.patientRepository = patientRepository;
		this.notificationService = notificationService;
		this.enabled = enabled;
	}

	/**
  - Ejecuta cada 10 minutos. Busca y envía recordatorios pendientes.
	 */
	@Scheduled(fixedDelayString = "${appointment.reminders.check-interval-ms:600000}")
	@Transactional
	public void run() {
		if (!enabled) return;

		LocalDateTime now = LocalDateTime.now();

		processReminders24h(now);
		processReminders2h(now);
	}

	// ─── 24h Reminders ────────────────────────────────────────────────

	private void processReminders24h(LocalDateTime now) {
		// Ventana: citas entre 23h y 25h desde ahora
		LocalDateTime windowStart = now.plusHours(23);
		LocalDateTime windowEnd = now.plusHours(25);

		List<AppointmentEntity> appointments =
				appointmentRepository.findPendingReminder24h(windowStart, windowEnd);

		if (!appointments.isEmpty()) {
			log.info("Processing {} reminder(s) of 24h", appointments.size());
		}

		for (AppointmentEntity appointment : appointments) {
			sendReminder(appointment, "24h");
		}

	}

	// ─── 2h Reminders ─────────────────────────────────────────────────

	private void processReminders2h(LocalDateTime now) {
		// Ventana: citas entre 1h30m y 2h30m desde ahora
		LocalDateTime windowStart = now.plusMinutes(90);
		LocalDateTime windowEnd = now.plusMinutes(150);

		List<AppointmentEntity> appointments =
				appointmentRepository.findPendingReminder2h(now, windowEnd);

		appointments = appointments.stream()
				.filter(a -> a.getScheduledAt().isAfter(windowStart))
				.toList();

		if (!appointments.isEmpty()) {
			log.info("Processing {} reminder(s) of 2h", appointments.size());
		}

		for (AppointmentEntity appointment : appointments) {
			sendReminder(appointment, "2h");
		}

	}

	// ─── Send ─────────────────────────────────────────────────────────

	private void sendReminder(AppointmentEntity appointment, String type) {
		try {
			String patientName = loadPatientName(appointment.getPatientId());
			String message = buildMessage(appointment, patientName, type);

			boolean sent = notificationService.sendAppointmentReminder(
					appointment.getTenantId(),
					appointment.getPatientId(),
					appointment.getId(),
					type,
					message
					);

			if (sent) {
				// Marcar como enviado para no re-enviar
				if ("24h".equals(type)) {
					appointment.setReminder24hSent(true);
				} else if ("2h".equals(type)) {
					appointment.setReminder2hSent(true);
				}
				appointmentRepository.save(appointment);

				log.debug("Reminder {} sent: appointment={}", type, appointment.getId());
			}

		} catch (Exception e) {
			log.error("Failed to send {} reminder for appointment {}: {}",
					type, appointment.getId(), e.getMessage());
			// No marcar como enviado → se reintentará en el próximo ciclo
		}

	}

	private String buildMessage(AppointmentEntity appointment, String patientName, String type) {
		String time = appointment.getScheduledAt().format(TIME_FORMAT);
		String date = appointment.getScheduledAt().format(DATE_FORMAT);

		if ("24h".equals(type)) {
			return String.format(
					"Hola %s, te recordamos que tienes una cita programada para mañana %s a las %s. " +
							"Si necesitas reprogramar, contáctanos con anticipación.",
							patientName, date, time);
		} else {
			return String.format(
					"Hola %s, tu cita es en 2 horas (%s). Te esperamos.",
					patientName, time);
		}

	}

	private String loadPatientName(java.util.UUID patientId) {
		return patientRepository.findById(patientId)
				.map(PatientEntity::getFullName)
				.orElse("Paciente");
	}
}
