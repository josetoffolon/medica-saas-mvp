package com.bisioneers.medica.consent.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.consent.domain.*;
import com.bisioneers.medica.consent.dto.ConsentDtos.*;
import com.bisioneers.medica.consent.service.*;
import com.bisioneers.medica.consent.service.ConsentVariableRenderer.RenderContext;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller para plantillas de consentimiento.
 *
 * Lectura: cualquier staff autenticado (necesitan elegir plantillas al firmar)
 * Mutación: solo ADMIN.
 *
 * Mejoras aplicadas:
 *  #1 Listado sin N+1 (un solo query con stats batch)
 *  #8 Cache-Control en endpoint de variables (lista estática)
 *  #9 listVersions retorna VersionSummary (sin contentHtml, más ligero)
 *  #10 getVersion retorna VersionResponse con contentHtml
 */
@RestController
@RequestMapping("/api/consent-templates")
public class ConsentTemplateController {

	private final ConsentTemplateService service;
	private final ConsentVariableCatalog variableCatalog;
	private final ConsentVariableRenderer renderer;
	private final PatientRepository patientRepository;
	private final TenantRepository tenantRepository;

	public ConsentTemplateController(ConsentTemplateService service,
			ConsentVariableCatalog variableCatalog,
			ConsentVariableRenderer renderer,
			PatientRepository patientRepository,
			TenantRepository tenantRepository) {
		this.service = service;
		this.variableCatalog = variableCatalog;
		this.renderer = renderer;
		this.patientRepository = patientRepository;
		this.tenantRepository = tenantRepository;
	}

	// ─── TEMPLATES ────────────────────────────────────────────────────

	@GetMapping
	public ResponseEntity<List<TemplateResponse>> list(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam(defaultValue = "false") boolean includeInactive
			) {
		UUID tenantId = principal.getTenantId();

		// Mejora #1: una sola query para todas las stats
		Map<UUID, TemplateVersionStats> stats = service.getStatsMap(tenantId);

		List<TemplateResponse> resp = service.list(tenantId, includeInactive).stream()
				.map(t -> toTemplateResponse(t, stats.get(t.getId())))
				.toList();

		return ResponseEntity.ok(resp);
	}

	@GetMapping("/{id}")
	public ResponseEntity<TemplateResponse> getById(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		ConsentTemplateEntity t = service.getById(principal.getTenantId(), id);
		Map<UUID, TemplateVersionStats> stats = service.getStatsMap(principal.getTenantId());
		return ResponseEntity.ok(toTemplateResponse(t, stats.get(t.getId())));
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TemplateResponse> create(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@Valid @RequestBody CreateTemplateRequest req
			) {
		ConsentTemplateEntity created = service.create(
				principal.getTenantId(), req, principal.getUserId());
		Map<UUID, TemplateVersionStats> stats = service.getStatsMap(principal.getTenantId());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(toTemplateResponse(created, stats.get(created.getId())));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<TemplateResponse> updateMetadata(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateTemplateRequest req
			) {
		ConsentTemplateEntity updated = service.updateMetadata(principal.getTenantId(), id, req);
		Map<UUID, TemplateVersionStats> stats = service.getStatsMap(principal.getTenantId());
		return ResponseEntity.ok(toTemplateResponse(updated, stats.get(updated.getId())));
	}

	@PatchMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> deactivate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		service.deactivate(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Plantilla desactivada"));
	}

	@PatchMapping("/{id}/activate")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> activate(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		service.activate(principal.getTenantId(), id);
		return ResponseEntity.ok(Map.of("message", "Plantilla activada"));
	}

	// ─── VERSIONS ─────────────────────────────────────────────────────

	/**
	 * Mejora #9: lista resumida sin contentHtml (mucho más ligero).
	 */
	@GetMapping("/{templateId}/versions")
	public ResponseEntity<List<VersionSummaryResponse>> listVersions(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID templateId
			) {
		List<VersionSummaryResponse> resp = service
				.listVersions(principal.getTenantId(), templateId)
				.stream().map(this::toVersionSummary).toList();
		return ResponseEntity.ok(resp);
	}

	/**
	 * Mejora #10: detalle de versión con contentHtml.
	 */
	@GetMapping("/versions/{versionId}")
	public ResponseEntity<VersionResponse> getVersion(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID versionId
			) {
		return ResponseEntity.ok(toVersionResponse(
				service.getVersion(principal.getTenantId(), versionId)));
	}

	@PostMapping("/{templateId}/versions/draft-from-latest")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<VersionResponse> createDraftFromLatest(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID templateId
			) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(toVersionResponse(
						service.createDraftFromLatest(
								principal.getTenantId(), templateId, principal.getUserId())));
	}

	@PutMapping("/versions/{versionId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<VersionResponse> updateDraft(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID versionId,
			@Valid @RequestBody UpdateDraftVersionRequest req
			) {
		return ResponseEntity.ok(toVersionResponse(
				service.updateDraft(principal.getTenantId(), versionId, req, principal.getUserId())));
	}

	@PostMapping("/versions/{versionId}/publish")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<VersionResponse> publish(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID versionId
			) {
		return ResponseEntity.ok(toVersionResponse(
				service.publish(principal.getTenantId(), versionId, principal.getUserId())));
	}

	@DeleteMapping("/versions/{versionId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> deleteDraft(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID versionId
			) {
		service.deleteDraft(principal.getTenantId(), versionId);
		return ResponseEntity.ok(Map.of("message", "Borrador eliminado"));
	}

	// ─── PREVIEW / RENDER ─────────────────────────────────────────────

	/**
	 * Renderiza la versión con valores reales del paciente si se pasa patientId,
	 * o con datos demo si no se pasa.
	 */
	@PostMapping("/versions/{versionId}/preview")
	public ResponseEntity<RenderedConsentResponse> preview(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID versionId,
			@RequestBody(required = false) RenderPreviewRequest req
			) {
		UUID tenantId = principal.getTenantId();
		ConsentTemplateVersionEntity v = service.getVersion(tenantId, versionId);

		RenderContext ctx = buildContext(tenantId, req);
		return ResponseEntity.ok(renderer.render(
				v.getId(), v.getVersionNumber(), v.getTitle(), v.getContentHtml(), ctx));
	}

	private RenderContext buildContext(UUID tenantId, RenderPreviewRequest req) {
		TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);

		PatientEntity patient = null;
		if (req != null && req.patientId() != null) {
			patient = patientRepository.findById(req.patientId())
					.filter(p -> p.getTenantId().equals(tenantId))
					.orElse(null);
		}

		// Datos del paciente (reales o demo)
		if (patient != null) {
			return new RenderContext(
					ZoneId.of("America/Panama"),
					patient.getFullName(),
					patient.getFirstName(),
					patient.getLastName(),
					patient.getDocumentType(),
					patient.getDocumentNumber(),
					patient.getEmail(),
					patient.getPhone(),
					patient.getBirthDate(),
					"M".equals(patient.getGender()) ? "Masculino" : "F".equals(patient.getGender()) ? "Femenino" : null,
							patient.getNationality(),
							patient.getAddress(),
							patient.getBloodType(),
							patient.getAllergies(),
							patient.getMedicalConditions(),
							patient.getCurrentMedications(),
							patient.getEmergencyContactName(),
							patient.getEmergencyContactPhone(),
							patient.getEmergencyContactRelation(),
							null, // appointment
							null, null, // service
							tenant != null ? tenant.getDisplayName() : null,
									tenant != null ? tenant.getAddress() : null,
											tenant != null ? tenant.getContactPhone() : null,
													tenant != null ? tenant.getContactEmail() : null
					);
		}

		// Demo: solo tenant real, paciente placeholder
		return new RenderContext(
				ZoneId.of("America/Panama"),
				"Juan Pérez González", "Juan", "Pérez",
				"CEDULA", "8-123-456",
				"demo@example.com", "+507 6000-0000",
				null, "Masculino", "PAN", "Calle ejemplo",
				"O+", "Ninguna", "Ninguna", "Ninguno",
				"Pedro Pérez", "+507 6000-0001", "PADRE",
				null, null, null,
				tenant != null ? tenant.getDisplayName() : "Mi Clínica",
						tenant != null ? tenant.getAddress() : "—",
								tenant != null ? tenant.getContactPhone() : "—",
										tenant != null ? tenant.getContactEmail() : "—"
				);
	}

	// ─── VARIABLES CATALOG ────────────────────────────────────────────

	/**
	 * Mejora #8: catálogo de variables cacheable (1 hora).
	 * Es lista estática que solo cambia con deploy.
	 */
	@GetMapping("/variables")
	public ResponseEntity<List<VariableDescriptor>> listVariables() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
				.body(variableCatalog.list());
	}

	// ─── MAPPERS ──────────────────────────────────────────────────────

	private TemplateResponse toTemplateResponse(ConsentTemplateEntity t, TemplateVersionStats stats) {
		Integer currentVN = stats != null ? stats.currentVersionNumber() : null;
		long totalV = stats != null && stats.totalVersions() != null ? stats.totalVersions() : 0;
		return new TemplateResponse(
				t.getId(), t.getName(), t.getCode(), t.getDescription(),
				t.getCurrentVersionId(), currentVN, t.isActive(), t.getDisplayOrder(),
				totalV
				);
	}

	private VersionSummaryResponse toVersionSummary(ConsentTemplateVersionEntity v) {
		return new VersionSummaryResponse(
				v.getId(), v.getTemplateId(), v.getVersionNumber(), v.getTitle(),
				v.getStatus(), v.getPublishedAt(), v.getPublishedByUserId(),
				v.getLastEditedAt(), v.getLastEditedByUserId()
				);
	}

	private VersionResponse toVersionResponse(ConsentTemplateVersionEntity v) {
		return new VersionResponse(
				v.getId(), v.getTemplateId(), v.getVersionNumber(), v.getTitle(),
				v.getContentHtml(), v.getStatus(),
				v.getPublishedAt(), v.getPublishedByUserId(),
				v.getLastEditedAt(), v.getLastEditedByUserId()
				);
	}
}
