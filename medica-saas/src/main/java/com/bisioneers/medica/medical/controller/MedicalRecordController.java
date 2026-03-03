package com.bisioneers.medica.medical.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.medical.domain.MedicalRecordEntity;
import com.bisioneers.medica.medical.dto.MedicalDtos.*;
import com.bisioneers.medica.medical.service.MedicalRecordService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**

- REST controller para historial clínico.
- 
- Endpoints:
- POST   /api/medical-records                          → Crear registro
- GET    /api/medical-records/{id}                     → Detalle
- GET    /api/patients/{patientId}/medical-records      → Historial del paciente (paginado)
- GET    /api/appointments/{appointmentId}/medical-records → Registros de una cita
- PUT    /api/medical-records/{id}                     → Actualizar (si no está firmado)
- PATCH  /api/medical-records/{id}/sign                → Firmar registro
- PATCH  /api/medical-records/{id}/unsign              → Des-firmar (ADMIN)
- 
- Acceso: ADMIN, MEDICO pueden crear/editar/firmar.
- RECEPCION y ASISTENTE pueden ver (lectura).
 */
@RestController
@RequestMapping("/api")
public class MedicalRecordController {

	private final MedicalRecordService recordService;

	public MedicalRecordController(MedicalRecordService recordService) {
		this.recordService = recordService;
	}

	// ─── CREATE ───────────────────────────────────────────────────────

	@PostMapping("/medical-records")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<RecordResponse> create(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody CreateRecordRequest request
			) {
		MedicalRecordEntity created = recordService.create(principal.getTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
	}

	// ─── READ ─────────────────────────────────────────────────────────

	@GetMapping("/medical-records/{id}")
	public ResponseEntity<RecordResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		MedicalRecordEntity record = recordService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(record));
	}

	/**
  - Historial clínico de un paciente (paginado).
  - Uso: GET /api/patients/{patientId}/medical-records?page=0&size=20
	 */
	@GetMapping("/patients/{patientId}/medical-records")
	public ResponseEntity<Page<RecordResponse>> getByPatient(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID patientId,
			@PageableDefault(size = 20) Pageable pageable
			) {
		Page<RecordResponse> records = recordService
				.getByPatient(principal.getTenantId(), patientId, pageable)
				.map(this::toResponse);

		return ResponseEntity.ok(records);
	}

	/**
  - Registros médicos asociados a una cita.
	 */
	@GetMapping("/appointments/{appointmentId}/medical-records")
	public ResponseEntity<List<RecordResponse>> getByAppointment(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID appointmentId
			) {
		List<RecordResponse> records = recordService
				.getByAppointment(principal.getTenantId(), appointmentId)
				.stream()
				.map(this::toResponse)
				.toList();

		return ResponseEntity.ok(records);
	}

	// ─── UPDATE ───────────────────────────────────────────────────────

	@PutMapping("/medical-records/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<RecordResponse> update(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateRecordRequest request
			) {
		MedicalRecordEntity updated = recordService.update(principal.getTenantId(), id, request);
		return ResponseEntity.ok(toResponse(updated));
	}

	// ─── SIGN / UNSIGN ───────────────────────────────────────────────

	@PatchMapping("/medical-records/{id}/sign")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<RecordResponse> sign(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		MedicalRecordEntity signed = recordService.sign(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(signed));
	}

	@PatchMapping("/medical-records/{id}/unsign")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<RecordResponse> unsign(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		MedicalRecordEntity unsigned = recordService.unsign(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(unsigned));
	}

	// ─── Mapper ───────────────────────────────────────────────────────

	private RecordResponse toResponse(MedicalRecordEntity e) {
		return new RecordResponse(
				e.getId(), e.getPatientId(), e.getAppointmentId(),
				e.getRecordDate(), e.getRecordType(), e.getTitle(),
				e.getContent(), e.getDiagnosis(), e.getTreatment(),
				e.getInstructions(), e.isSigned(), e.isPatientVisible()
				);
	}
}
