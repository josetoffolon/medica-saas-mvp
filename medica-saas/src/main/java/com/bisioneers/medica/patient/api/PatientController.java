package com.bisioneers.medica.patient.api;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.dto.CreatePatientRequest;
import com.bisioneers.medica.patient.dto.PatientResponse;
import com.bisioneers.medica.patient.dto.UpdatePatientRequest;
import com.bisioneers.medica.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**

- REST controller para gestión de pacientes.
- 
- CAMBIOS vs versión anterior:
- - Usa @AuthenticationPrincipal StaffUserPrincipal (consistente con otros controllers)
- en vez de Authentication + cast manual a TenantAware
- - Agregado: PATCH /api/patients/{id}/consent → actualizar consentimientos
- - Agregado: PATCH /api/patients/{id}/reactivate → reactivar paciente
- - Búsqueda multi-campo: GET /api/patients?search=X busca en nombre, email, teléfono, documento
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

	private final PatientService patientService;

	public PatientController(PatientService patientService) {
		this.patientService = patientService;
	}

	// ─── CREATE ───────────────────────────────────────────────────────

	@PostMapping
	public ResponseEntity<PatientResponse> create(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody CreatePatientRequest request) {

		UUID tenantId = principal.getTenantId();

		PatientEntity entity = new PatientEntity();
		entity.setTenantId(tenantId);
		entity.setFullName(request.fullName());
		entity.setEmail(request.email());
		entity.setPhone(request.phone());
		entity.setSecondaryPhone(request.secondaryPhone());
		entity.setDocumentType(request.documentType());
		entity.setDocumentNumber(request.documentNumber());
		entity.setBirthDate(request.birthDate());
		entity.setGender(request.gender());
		entity.setAddress(request.address());
		entity.setNotes(request.notes());
		entity.setPhotoConsent(request.photoConsent());
		entity.setDataConsent(request.dataConsent());

		PatientEntity created = patientService.create(entity);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(PatientResponse.from(created));

	}

	// ─── READ ─────────────────────────────────────────────────────────

	@GetMapping("/{id}")
	public ResponseEntity<PatientResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		PatientEntity patient = patientService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(PatientResponse.from(patient));

	}

	/**
  - Listar pacientes activos, con búsqueda multi-campo opcional.
  - 
  - GET /api/patients                    → todos los pacientes activos (paginado)
  - GET /api/patients?search=josé        → busca en nombre, email, teléfono, documento
  - GET /api/patients?search=6000-1234   → encuentra por teléfono
  - GET /api/patients?search=8-888-1234  → encuentra por documento
	 */
	@GetMapping
	public ResponseEntity<Page<PatientResponse>> list(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam(required = false) String search,
			@PageableDefault(size = 20) Pageable pageable) {

		UUID tenantId = principal.getTenantId();

		Page<PatientEntity> patients;
		if (search != null && !search.isBlank()) {
			patients = patientService.search(tenantId, search, pageable);
		} else {
			patients = patientService.listActive(tenantId, pageable);
		}

		return ResponseEntity.ok(patients.map(PatientResponse::from));
	}

	@GetMapping("/count")
	public ResponseEntity<Long> count(
			@AuthenticationPrincipal StaffUserPrincipal principal) {
		long count = patientService.countActive(principal.getTenantId());
		return ResponseEntity.ok(count);
	}

	// ─── UPDATE ───────────────────────────────────────────────────────

	@PutMapping("/{id}")
	public ResponseEntity<PatientResponse> update(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdatePatientRequest request) {

		UUID tenantId = principal.getTenantId();

		PatientEntity updates = new PatientEntity();
		updates.setTenantId(tenantId);
		updates.setFullName(request.fullName());
		updates.setEmail(request.email());
		updates.setPhone(request.phone());
		updates.setSecondaryPhone(request.secondaryPhone());
		updates.setDocumentType(request.documentType());
		updates.setDocumentNumber(request.documentNumber());
		updates.setBirthDate(request.birthDate());
		updates.setGender(request.gender());
		updates.setAddress(request.address());
		updates.setNotes(request.notes());
		updates.setPhotoConsent(request.photoConsent());
		updates.setDataConsent(request.dataConsent());

		PatientEntity updated = patientService.update(id, updates);
		return ResponseEntity.ok(PatientResponse.from(updated));

	}

	// ─── CONSENT ──────────────────────────────────────────────────────

	/**
  - Actualizar consentimientos del paciente.
  - 
  - NUEVO: Este endpoint expone PatientService.updateConsent() que
  - existía pero no tenía ruta en el controller.
  - 
  - Uso: cuando el paciente firma/revoca consentimiento de fotos o datos,
  - el frontend solo necesita enviar los 2 campos de consentimiento,
  - no todo el formulario de edición del paciente.
	 */
	@PatchMapping("/{id}/consent")
	public ResponseEntity<Map<String, Object>> updateConsent(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@RequestBody Map<String, Boolean> consentData) {

		boolean photoConsent = consentData.getOrDefault("photoConsent", false);
		boolean dataConsent = consentData.getOrDefault("dataConsent", false);

		patientService.updateConsent(principal.getTenantId(), id, photoConsent, dataConsent);

		return ResponseEntity.ok(Map.of(
				"message", "Consentimientos actualizados",
				"photoConsent", photoConsent,
				"dataConsent", dataConsent
				));
	}

	// ─── DEACTIVATE / REACTIVATE ──────────────────────────────────────

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deactivate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		patientService.deactivate(principal.getTenantId(), id);
		return ResponseEntity.noContent().build();

	}

	/**
  - Reactivar un paciente previamente desactivado.
  - NUEVO: No existía endpoint para restaurar pacientes.
	 */
	@PatchMapping("/{id}/reactivate")
	public ResponseEntity<Map<String, String>> reactivate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		patientService.reactivate(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Paciente reactivado"));
	}
}