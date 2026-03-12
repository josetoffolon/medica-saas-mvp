package com.bisioneers.medica.medical.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.medical.domain.MedicalPhotoEntity;
import com.bisioneers.medica.medical.dto.MedicalDtos.*;
import com.bisioneers.medica.medical.service.MedicalPhotoService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**

- REST controller para fotos médicas.
- 
- Endpoints:
- POST   /api/medical-photos/upload                     → Subir foto (multipart)
- GET    /api/medical-photos/{id}                       → Metadata de una foto
- GET    /api/medical-photos/{id}/download              → Descargar/ver imagen
- GET    /api/patients/{patientId}/photos                → Fotos de un paciente
- GET    /api/patients/{patientId}/photos/pairs          → Pares antes/después
- GET    /api/medical-records/{recordId}/photos          → Fotos de un registro
- GET    /api/appointments/{appointmentId}/photos        → Fotos de una cita
- DELETE /api/medical-photos/{id}                       → Eliminar foto (ADMIN)
- 
- Upload: multipart/form-data con campo “file” (imagen) + campos de metadata.
 */
@RestController
@RequestMapping("/api")
public class MedicalPhotoController {

	private final MedicalPhotoService photoService;

	public MedicalPhotoController(MedicalPhotoService photoService) {
		this.photoService = photoService;
	}

	// ─── UPLOAD ───────────────────────────────────────────────────────

	/**
  - Sube una foto médica.
  - 
  - Content-Type: multipart/form-data
  - - file: archivo de imagen (JPEG, PNG, WebP, max 10MB)
  - - patientId: UUID del paciente (obligatorio)
  - - photoType: BEFORE, AFTER, PROGRESS, OTHER (obligatorio)
  - - medicalRecordId: UUID del registro médico (opcional)
  - - appointmentId: UUID de la cita (opcional)
  - - anatomicalArea: área fotografiada (opcional)
  - - notes: notas sobre la foto (opcional)
  - - consentGiven: boolean (opcional, default false)
  - - patientVisible: boolean (opcional, default false)
  - - pairedPhotoId: UUID de la foto pareja (opcional)
	 */
	@PostMapping("/medical-photos/upload")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<PhotoResponse> upload(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam("file") MultipartFile file,
			@RequestParam("patientId") UUID patientId,
			@RequestParam("photoType") String photoType,
			@RequestParam(value = "medicalRecordId", required = false) UUID medicalRecordId,
			@RequestParam(value = "appointmentId", required = false) UUID appointmentId,
			@RequestParam(value = "anatomicalArea", required = false) String anatomicalArea,
			@RequestParam(value = "notes", required = false) String notes,
			@RequestParam(value = "consentGiven", required = false) Boolean consentGiven,
			@RequestParam(value = "patientVisible", required = false) Boolean patientVisible,
			@RequestParam(value = "pairedPhotoId", required = false) UUID pairedPhotoId
			) {
		PhotoMetadata metadata = new PhotoMetadata(
				patientId, medicalRecordId, appointmentId,
				photoType, anatomicalArea, notes,
				consentGiven, patientVisible, pairedPhotoId
				);

		MedicalPhotoEntity saved = photoService.upload(principal.getTenantId(), metadata, file);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
	}

	// ─── READ (metadata) ─────────────────────────────────────────────

	@GetMapping("/medical-photos/{id}")
	public ResponseEntity<PhotoResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		MedicalPhotoEntity photo = photoService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(photo));
	}

	// ─── DOWNLOAD (imagen) ───────────────────────────────────────────

	/**
  - Descarga/visualiza la imagen.
  - Retorna el archivo binario con Content-Type correcto para renderizar en el browser.
	 */
	@GetMapping("/medical-photos/{id}/download")
	public ResponseEntity<InputStreamResource> download(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		MedicalPhotoEntity photo = photoService.getById(principal.getTenantId(), id);
		InputStream stream = photoService.downloadPhoto(principal.getTenantId(), id);

		String mimeType = photo.getMimeType() != null ? photo.getMimeType() : "application/octet-stream";

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(mimeType))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"inline; filename=''" + photo.getOriginalFilename() + "''")
				.body(new InputStreamResource(stream));
	}

	// ─── LIST BY PATIENT ──────────────────────────────────────────────

	@GetMapping("/patients/{patientId}/photos")
	public ResponseEntity<List<PhotoResponse>> getByPatient(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID patientId
			) {
		List<PhotoResponse> photos = photoService
				.getByPatient(principal.getTenantId(), patientId)
				.stream()
				.map(this::toResponse)
				.toList();

		return ResponseEntity.ok(photos);

	}

	/**
  - Pares de fotos antes/después de un paciente.
	 */
	@GetMapping("/patients/{patientId}/photos/pairs")
	public ResponseEntity<List<PhotoPairResponse>> getPairedPhotos(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID patientId
			) {
		List<PhotoPairResponse> pairs = photoService
				.getPairedPhotos(principal.getTenantId(), patientId);

		return ResponseEntity.ok(pairs);
	}

	// ─── LIST BY RECORD / APPOINTMENT ─────────────────────────────────

	@GetMapping("/medical-records/{recordId}/photos")
	public ResponseEntity<List<PhotoResponse>> getByRecord(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID recordId
			) {
		List<PhotoResponse> photos = photoService
				.getByRecord(principal.getTenantId(), recordId)
				.stream()
				.map(this::toResponse)
				.toList();

		return ResponseEntity.ok(photos);

	}

	@GetMapping("/appointments/{appointmentId}/photos")
	public ResponseEntity<List<PhotoResponse>> getByAppointment(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID appointmentId
			) {
		List<PhotoResponse> photos = photoService
				.getByAppointment(principal.getTenantId(), appointmentId)
				.stream()
				.map(this::toResponse)
				.toList();

		return ResponseEntity.ok(photos);

	}

	// ─── DELETE ───────────────────────────────────────────────────────

	@DeleteMapping("/medical-photos/{id}")
	@PreAuthorize("hasRole(‘ADMIN’)")
	public ResponseEntity<Map<String, String>> delete(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		photoService.delete(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Foto eliminada correctamente"));
	}

	// ─── Mapper ───────────────────────────────────────────────────────

	private PhotoResponse toResponse(MedicalPhotoEntity e) {
		return new PhotoResponse(
				e.getId(), e.getPatientId(), e.getMedicalRecordId(),
				e.getAppointmentId(), e.getPhotoType(), e.getStoragePath(),
				e.getOriginalFilename(), e.getMimeType(), e.getFileSize(),
				e.getCapturedAt(), e.getAnatomicalArea(), e.getNotes(),
				e.isConsentGiven(), e.isPatientVisible(), e.getPairedPhotoId()
				);
	}
}