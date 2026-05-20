package com.bisioneers.medica.documents.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.documents.domain.PatientDocumentEntity;
import com.bisioneers.medica.documents.dto.DocumentDtos.*;
import com.bisioneers.medica.documents.service.PatientDocumentService;
import com.bisioneers.medica.medical.storage.MediaStorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PatientDocumentController {

	private final PatientDocumentService documentService;
	private final MediaStorageService storageService;

	public PatientDocumentController(PatientDocumentService documentService,
			MediaStorageService storageService) {
		this.documentService = documentService;
		this.storageService = storageService;
	}

	// ─── GENERATE (DRAFT) ────────────────────────────────────────────

	@PostMapping("/patients/{patientId}/documents")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<DocumentResponse> generate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID patientId,
			@Valid @RequestBody GenerateDocumentRequest req
			) {
		if (!patientId.equals(req.patientId())) {
			throw new IllegalArgumentException("patientId mismatch");
		}
		PatientDocumentEntity doc = documentService.generate(
				principal.getTenantId(), patientId, req.templateId(), req.title());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(doc));
	}

	// ─── LIST ─────────────────────────────────────────────────────────

	@GetMapping("/patients/{patientId}/documents")
	public ResponseEntity<List<DocumentSummaryResponse>> listByPatient(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID patientId
			) {
		List<PatientDocumentEntity> docs = documentService.listByPatient(
				principal.getTenantId(), patientId);
		return ResponseEntity.ok(docs.stream().map(this::toSummary).toList());
	}

	// ─── READ ─────────────────────────────────────────────────────────

	@GetMapping("/documents/{id}")
	public ResponseEntity<DocumentResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		PatientDocumentEntity doc = documentService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(doc));
	}

	// ─── UPDATE CONTENT (solo DRAFT) ──────────────────────────────────

	@PutMapping("/documents/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<DocumentResponse> updateContent(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateDocumentContentRequest req
			) {
		PatientDocumentEntity updated = documentService.updateContent(
				principal.getTenantId(), id, req.renderedHtml(), req.title());
		return ResponseEntity.ok(toResponse(updated));
	}

	// ─── PREPARE FOR SIGNING ──────────────────────────────────────────

	@PostMapping("/documents/{id}/prepare")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<DocumentResponse> prepareForSigning(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		PatientDocumentEntity doc = documentService.prepareForSigning(
				principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(doc));
	}

	// ─── ARCHIVE ──────────────────────────────────────────────────────

	@PostMapping("/documents/{id}/archive")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DocumentResponse> archive(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		PatientDocumentEntity doc = documentService.archive(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(doc));
	}

	// ─── DELETE (solo DRAFT) ──────────────────────────────────────────

	@DeleteMapping("/documents/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO')")
	public ResponseEntity<Map<String, String>> delete(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		documentService.deleteDraft(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Documento eliminado"));
	}

	// ─── Mappers ──────────────────────────────────────────────────────

	private DocumentResponse toResponse(PatientDocumentEntity e) {
		String pdfUrl = e.getPdfStorageKey() != null
				? storageService.generateAccessUrl(e.getPdfStorageKey()) : null;
		String signedPdfUrl = e.getSignedPdfStorageKey() != null
				? storageService.generateAccessUrl(e.getSignedPdfStorageKey()) : null;

		return new DocumentResponse(
				e.getId(), e.getPatientId(), e.getTemplateId(), e.getTemplateName(),
				e.getDocumentType(), e.getTitle(), e.getRenderedHtml(), e.getStatus(),
				pdfUrl, signedPdfUrl, e.getSignatureMethod(), e.getIntegrityHash(),
				e.getGeneratedAt(), e.getSignedAt(), e.getSignerName()
				);
	}

	private DocumentSummaryResponse toSummary(PatientDocumentEntity e) {
		return new DocumentSummaryResponse(
				e.getId(), e.getPatientId(), e.getTemplateName(),
				e.getDocumentType(), e.getTitle(), e.getStatus(),
				e.getSignatureMethod(), e.getGeneratedAt(), e.getSignedAt()
				);
	}
}
