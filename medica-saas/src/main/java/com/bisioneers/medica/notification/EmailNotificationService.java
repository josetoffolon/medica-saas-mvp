package com.bisioneers.medica.notification;

import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**

- Envío de notificaciones por email via SMTP.
- 
- Usa Spring Boot’s JavaMailSender, configurado con las properties existentes:
- spring.mail.host, spring.mail.port, spring.mail.username, spring.mail.password
- 
- Para HTML emails (fase 2): usar JavaMailSender.createMimeMessage() + Thymeleaf templates.
 */
@Service("emailNotificationService")
public class EmailNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

	private final MailSender mailSender;
	private final PatientRepository patientRepository;
	private final String fromAddress;

	public EmailNotificationService(
			MailSender mailSender,
			PatientRepository patientRepository,
			@Value("${spring.mail.username:noreply@medica.com}") String fromAddress
			) {
		this.mailSender = mailSender;
		this.patientRepository = patientRepository;
		this.fromAddress = fromAddress;
	}

	@Override
	public boolean sendAppointmentReminder(UUID tenantId, UUID patientId,
			UUID appointmentId, String reminderType,
			String message) {
		PatientEntity patient = patientRepository.findById(patientId).orElse(null);
		if (patient == null || patient.getEmail() == null || patient.getEmail().isBlank()) {
			log.debug("Email reminder skipped: no email for patient {}", patientId);
			return false;
		}
		String subject = "2h".equals(reminderType)
				? "Recordatorio: Tu cita es pronto"
						: "Recordatorio: Tienes una cita mañana";
		return send(patient.getEmail(), subject, message);
	}

	@Override
	public boolean send(String to, String subject, String body) {
		try {
			SimpleMailMessage mail = new SimpleMailMessage();
			mail.setFrom(fromAddress);
			mail.setTo(to);
			mail.setSubject(subject);
			mail.setText(body);
			mailSender.send(mail);
			log.info("Email sent: to={}, subject={}", to, subject);
			return true;
		} catch (Exception e) {
			log.error("Email failed: to={}, error={}", to, e.getMessage());
			return false;
		}
	}

	@Override
	public String getChannel() {
		return "EMAIL";
	}
}