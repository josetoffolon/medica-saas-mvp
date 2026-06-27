package com.bisioneers.medica.appointment.service;

import com.bisioneers.medica.appointment.domain.AppointmentEntity;
import com.bisioneers.medica.appointment.domain.AppointmentRepository;
import com.bisioneers.medica.service.domain.ServiceEntity;
import com.bisioneers.medica.service.domain.ServiceRepository;
import com.bisioneers.medica.tenant.domain.TenantRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**

- Servicio de citas.
- 
- CAMBIOS vs versión anterior:
- - create() y update() ahora validan horarios laborales del tenant
- via BusinessHoursService (lee de TenantEntity.settings)
 */
@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;
	private final ServiceRepository serviceRepository;
	private final BusinessHoursService businessHoursService;
	private final TenantRepository tenantRepository;

	public AppointmentService(AppointmentRepository appointmentRepository,
			ServiceRepository serviceRepository,
			BusinessHoursService businessHoursService,
			TenantRepository tenantRepository) {
		this.appointmentRepository = appointmentRepository;
		this.serviceRepository = serviceRepository;
		this.businessHoursService = businessHoursService;
		this.tenantRepository = tenantRepository;
	}

	@Transactional
	public AppointmentEntity create(AppointmentEntity appointment) {

		// Lock pesimista sobre el tenant: serializa la creación de citas del mismo
		// tenant para cerrar la ventana check-then-act del conflicto de horario.
		// Dos peticiones concurrentes del mismo tenant se procesan en orden;
		// tenants distintos no se bloquean entre sí.
		tenantRepository.findByIdForUpdate(appointment.getTenantId())
		.orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

		// Si hay serviceId, cargar duración del servicio
		if (appointment.getServiceId() != null) {
			ServiceEntity service = serviceRepository.findById(appointment.getServiceId())
					.orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));

			if (!service.getTenantId().equals(appointment.getTenantId())) {
				throw new IllegalArgumentException("Servicio no pertenece al tenant");
			}

			if (appointment.getDurationMinutes() == 0) {
				appointment.setDurationMinutes(service.getDurationMinutes());
			}
		}

		// Validar horario laboral del tenant
		businessHoursService.validate(
				appointment.getTenantId(),
				appointment.getScheduledAt(),
				appointment.getDurationMinutes()
				);

		// Validar que no haya choque de horario
		LocalDateTime endTime = appointment.getScheduledAt()
				.plusMinutes(appointment.getDurationMinutes());

		boolean hasConflict = appointmentRepository.hasConflict(
				appointment.getTenantId(),
				appointment.getScheduledAt(),
				endTime
				);

		if (hasConflict) {
			throw new IllegalArgumentException(
					"Ya existe una cita en ese horario. Por favor seleccione otro horario.");
		}

		if (appointment.getStatus() == null || appointment.getStatus().isBlank()) {
			appointment.setStatus("SCHEDULED");
		}

		return appointmentRepository.save(appointment);
	}

	@Transactional
	public AppointmentEntity update(UUID id, AppointmentEntity updates) {
		AppointmentEntity existing = appointmentRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));

		if (!existing.getTenantId().equals(updates.getTenantId())) {
			throw new IllegalArgumentException("No se puede cambiar el tenant de la cita");
		}

		// Lock pesimista sobre el tenant: serializa la creación de citas del mismo
		// tenant para cerrar la ventana check-then-act del conflicto de horario.
		// Dos peticiones concurrentes del mismo tenant se procesan en orden;
		// tenants distintos no se bloquean entre sí.
		tenantRepository.findByIdForUpdate(updates.getTenantId())
		.orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

		// Si se cambia la fecha/hora o duración, re-validar
		if (!existing.getScheduledAt().equals(updates.getScheduledAt()) ||
				existing.getDurationMinutes() != updates.getDurationMinutes()) {

			// Validar horario laboral
			businessHoursService.validate(
					updates.getTenantId(),
					updates.getScheduledAt(),
					updates.getDurationMinutes()
					);

			// Validar conflictos (excluyendo esta cita)
			LocalDateTime endTime = updates.getScheduledAt()
					.plusMinutes(updates.getDurationMinutes());

			boolean hasConflict = appointmentRepository.hasConflictExcluding(
					updates.getTenantId(),
					updates.getScheduledAt(),
					endTime,
					id
					);

			if (hasConflict) {
				throw new IllegalArgumentException(
						"Ya existe una cita en ese horario. Por favor seleccione otro horario.");
			}
		}

		existing.setPatientId(updates.getPatientId());
		existing.setServiceId(updates.getServiceId());
		existing.setScheduledAt(updates.getScheduledAt());
		existing.setDurationMinutes(updates.getDurationMinutes());
		existing.setStatus(updates.getStatus());
		existing.setReason(updates.getReason());
		existing.setStaffNotes(updates.getStaffNotes());
		existing.setPatientNotes(updates.getPatientNotes());

		return appointmentRepository.save(existing);
	}

	@Transactional(readOnly = true)
	public AppointmentEntity getById(UUID tenantId, UUID appointmentId) {
		AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));

		if (!appointment.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado");
		}

		return appointment;
	}

	@Transactional(readOnly = true)
	public List<AppointmentEntity> getByDateRange(UUID tenantId,
			LocalDateTime start,
			LocalDateTime end) {
		return appointmentRepository.findByTenantAndDateRange(tenantId, start, end);
	}

	@Transactional(readOnly = true)
	public Page<AppointmentEntity> getByPatient(UUID tenantId, UUID patientId, Pageable pageable) {
		return appointmentRepository.findByTenantIdAndPatientIdOrderByScheduledAtDesc(
				tenantId, patientId, pageable);
	}

	@Transactional
	public void confirm(UUID tenantId, UUID appointmentId) {
		AppointmentEntity appointment = getById(tenantId, appointmentId);
		appointment.setStatus("CONFIRMED");
		appointment.setConfirmedAt(Instant.now());
		appointmentRepository.save(appointment);
	}

	@Transactional
	public void cancel(UUID tenantId, UUID appointmentId, String reason) {
		AppointmentEntity appointment = getById(tenantId, appointmentId);

		if ("CANCELLED".equals(appointment.getStatus())) {
			throw new IllegalArgumentException("La cita ya está cancelada");
		}

		appointment.setStatus("CANCELLED");
		appointment.setCancelledAt(Instant.now());
		appointment.setCancellationReason(reason);
		appointmentRepository.save(appointment);
	}

	@Transactional
	public void complete(UUID tenantId, UUID appointmentId) {
		AppointmentEntity appointment = getById(tenantId, appointmentId);
		appointment.setStatus("COMPLETED");
		appointmentRepository.save(appointment);
	}

	@Transactional
	public void markNoShow(UUID tenantId, UUID appointmentId) {
		AppointmentEntity appointment = getById(tenantId, appointmentId);
		appointment.setStatus("NO_SHOW");
		appointmentRepository.save(appointment);
	}

	@Transactional
	public void markReminderSent(UUID appointmentId, String reminderType) {
		AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));

		if ("24h".equals(reminderType)) {
			appointment.setReminder24hSent(true);
		} else if ("2h".equals(reminderType)) {
			appointment.setReminder2hSent(true);
		}

		appointmentRepository.save(appointment);
	}

	@Transactional(readOnly = true)
	public List<AppointmentEntity> getPendingReminders24h(LocalDateTime now, LocalDateTime windowEnd) {
		return appointmentRepository.findPendingReminder24h(now, windowEnd);
	}

	@Transactional(readOnly = true)
	public List<AppointmentEntity> getPendingReminders2h(LocalDateTime now, LocalDateTime windowEnd) {
		return appointmentRepository.findPendingReminder2h(now, windowEnd);
	}
}