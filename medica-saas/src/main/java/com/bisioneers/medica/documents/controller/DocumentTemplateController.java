package com.bisioneers.medica.documents.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.documents.domain.DocumentTemplateEntity;
import com.bisioneers.medica.documents.dto.DocumentDtos.*;
import com.bisioneers.medica.documents.service.DocumentTemplateService;
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
@RequestMapping("/api/document-templates")
public class DocumentTemplateController {

	private final DocumentTemplateService templateService;

	public DocumentTemplateController(DocumentTemplateService templateService) {
		this.templateService = templateService;
	}

	/** Lista plantillas activas. Si se pasa ?type=X filtra por tipo. */
	@GetMapping
	public ResponseEntity<List<TemplateSummaryResponse>> list(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam(required = false) String type
			) {
		List<DocumentTemplateEntity> templates = type != null && !type.isBlank()
				? templateService.listByType(principal.getTenantId(), type)
						: templateService.listActive(principal.getTenantId());

		return ResponseEntity.ok(templates.stream().map(this::toSummary).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<TemplateResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		DocumentTemplateEntity template = templateService.getById(principal.getTenantId(), id);
		return ResponseEntity.ok(toResponse(template));
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TemplateResponse> create(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody CreateTemplateRequest req
			) {
		DocumentTemplateEntity saved = templateService.create(
				principal.getTenantId(),
				req.name(),
				req.documentType(),
				req.contentHtml(),
				req.description()
				);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TemplateResponse> update(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateTemplateRequest req
			) {
		DocumentTemplateEntity updated = templateService.update(
				principal.getTenantId(), id,
				req.name(), req.contentHtml(), req.description()
				);
		return ResponseEntity.ok(toResponse(updated));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> delete(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		templateService.deactivate(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Plantilla desactivada"));
	}

	// ─── Mappers ───────────────────────────────────────────

	private TemplateResponse toResponse(DocumentTemplateEntity e) {
		return new TemplateResponse(
				e.getId(), e.getName(), e.getDocumentType(),
				e.getContentHtml(), e.getDescription(),
				e.getVersion(), e.isActive(), e.isSystem()
				);
	}

	private TemplateSummaryResponse toSummary(DocumentTemplateEntity e) {
		return new TemplateSummaryResponse(
				e.getId(), e.getName(), e.getDocumentType(),
				e.getDescription(), e.getVersion(), e.isSystem()
				);
	}
}
