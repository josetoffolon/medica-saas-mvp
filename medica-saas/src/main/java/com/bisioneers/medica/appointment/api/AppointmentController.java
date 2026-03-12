package com.bisioneers.medica.appointment.api;

import com.bisioneers.medica.appointment.domain.AppointmentEntity;
import com.bisioneers.medica.appointment.dto.AppointmentResponse;
import com.bisioneers.medica.appointment.dto.CancelAppointmentRequest;
import com.bisioneers.medica.appointment.dto.CreateAppointmentRequest;
import com.bisioneers.medica.appointment.dto.UpdateAppointmentRequest;
import com.bisioneers.medica.appointment.service.AppointmentService;
import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**

- REST controller para citas médicas.
- 
- CAMBIOS vs versión anterior:
- - Usa @AuthenticationPrincipal StaffUserPrincipal (consistente con otros controllers)
- en vez de Authentication + cast manual a TenantAware
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

	private final AppointmentService appointmentService;

	public AppointmentController(AppointmentService appointmentService) {
		this.appointmentService = appointmentService;
	}

	@PostMapping
	public ResponseEntity<AppointmentResponse> create(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody CreateAppointmentRequest request) {

		UUID tenantId = principal.getTenantId();

		AppointmentEntity entity = new AppointmentEntity();
		entity.setTenantId(tenantId);
		entity.setPatientId(request.patientId());
		entity.setServiceId(request.serviceId());
		entity.setScheduledAt(request.scheduledAt());
		entity.setDurationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 30);
		entity.setReason(request.reason());
		entity.setStaffNotes(request.staffNotes());
		entity.setPatientNotes(request.patientNotes());

		AppointmentEntity created = appointmentService.create(entity);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(AppointmentResponse.from(created));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AppointmentResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		AppointmentEntity appointment = appointmentService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(AppointmentResponse.from(appointment));
	}

	/**
	 * Obtener citas por rango de fechas.
	 * GET /api/appointments?startDate=2024-01-01&endDate=2024-01-31
	 */
	@GetMapping
	public ResponseEntity<List<AppointmentResponse>> getByDateRange(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		UUID tenantId = principal.getTenantId();

		LocalDateTime start = startDate.atStartOfDay();
		LocalDateTime end = endDate.plusDays(1).atStartOfDay();

		List<AppointmentEntity> appointments = appointmentService.getByDateRange(tenantId, start, end);
		return ResponseEntity.ok(appointments.stream()
				.map(AppointmentResponse::from)
				.toList());
	}

	/**
	 * Obtener citas de un paciente específico.
	 * GET /api/appointments/patient/{patientId}
	 */
	@GetMapping("/patient/{patientId}")
	public ResponseEntity<Page<AppointmentResponse>> getByPatient(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID patientId,
			@PageableDefault(size = 20) Pageable pageable) {

		Page<AppointmentEntity> appointments =
				appointmentService.getByPatient(principal.getTenantId(), patientId, pageable);
		return ResponseEntity.ok(appointments.map(AppointmentResponse::from));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AppointmentResponse> update(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateAppointmentRequest request) {

		UUID tenantId = principal.getTenantId();

		AppointmentEntity updates = new AppointmentEntity();
		updates.setTenantId(tenantId);
		updates.setPatientId(request.patientId());
		updates.setServiceId(request.serviceId());
		updates.setScheduledAt(request.scheduledAt());
		updates.setDurationMinutes(request.durationMinutes());
		updates.setReason(request.reason());
		updates.setStaffNotes(request.staffNotes());
		updates.setPatientNotes(request.patientNotes());
		updates.setStatus(request.status());

		AppointmentEntity updated = appointmentService.update(id, updates);
		return ResponseEntity.ok(AppointmentResponse.from(updated));
	}

	@PostMapping("/{id}/confirm")
	public ResponseEntity<Void> confirm(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		appointmentService.confirm(principal.getTenantId(), id);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{id}/cancel")
	public ResponseEntity<Void> cancel(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody CancelAppointmentRequest request) {

		appointmentService.cancel(principal.getTenantId(), id, request.reason());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{id}/complete")
	public ResponseEntity<Void> complete(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		appointmentService.complete(principal.getTenantId(), id);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{id}/no-show")
	public ResponseEntity<Void> markNoShow(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		appointmentService.markNoShow(principal.getTenantId(), id);
		return ResponseEntity.ok().build();
	}
}