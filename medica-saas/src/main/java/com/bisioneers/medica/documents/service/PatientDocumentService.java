package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.consent.domain.ConsentTemplateVersionEntity;
import com.bisioneers.medica.consent.domain.ConsentVersionStatus;
import com.bisioneers.medica.consent.dto.ConsentDtos.RenderedConsentResponse;
import com.bisioneers.medica.consent.service.ConsentTemplateService;
import com.bisioneers.medica.consent.service.ConsentVariableRenderer;
import com.bisioneers.medica.consent.service.ConsentVariableRenderer.RenderContext;
import com.bisioneers.medica.documents.domain.PatientDocumentEntity;
import com.bisioneers.medica.documents.domain.PatientDocumentRepository;
import com.bisioneers.medica.medical.storage.MediaStorageService;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para el ciclo de vida de documentos del paciente.
 *
 * Flujo:
 *  1. generate() → renderiza ConsentTemplateVersion PUBLISHED con datos del paciente,
 *     crea PatientDocument en DRAFT con snapshot del HTML renderizado
 *  2. updateContent() → permite ajustes manuales mientras está DRAFT
 *  3. prepareForSigning() → genera PDF desde HTML, sube a R2, status READY_TO_SIGN
 *  4. [Fase 3] signDigital() → embed firma canvas, hash, SIGNED
 *  5. [Fase 4] uploadSigned() → recibe PDF escaneado firmado, SIGNED
 *
 * REFACTOR: ahora apunta al módulo `consent` para obtener plantillas.
 * El MergeFieldEngine viejo fue reemplazado por ConsentVariableRenderer
 * (mejor: escapa valores HTML).
 */
@Service
public class PatientDocumentService {

	private static final Logger log = LoggerFactory.getLogger(PatientDocumentService.class);
	private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Panama");

	private final PatientDocumentRepository documentRepository;
	private final ConsentTemplateService consentService;
	private final ConsentVariableRenderer consentRenderer;
	private final PatientRepository patientRepository;
	private final TenantRepository tenantRepository;
	private final PdfRenderer pdfRenderer;
	private final MediaStorageService storageService;

	public PatientDocumentService(PatientDocumentRepository documentRepository,
			ConsentTemplateService consentService,
			ConsentVariableRenderer consentRenderer,
			PatientRepository patientRepository,
			TenantRepository tenantRepository,
			PdfRenderer pdfRenderer,
			MediaStorageService storageService) {
		this.documentRepository = documentRepository;
		this.consentService = consentService;
		this.consentRenderer = consentRenderer;
		this.patientRepository = patientRepository;
		this.tenantRepository = tenantRepository;
		this.pdfRenderer = pdfRenderer;
		this.storageService = storageService;
	}

	// ─── GENERATE (DRAFT) ─────────────────────────────────────────────

	/**
	 * Genera un documento DRAFT renderizando una versión PUBLISHED del consent.
	 * El HTML renderizado se guarda como snapshot inmutable.
	 */
	@Transactional
	public PatientDocumentEntity generate(UUID tenantId, UUID patientId,
			UUID consentVersionId, String customTitle) {

		// 1. Validar la versión del consent
		ConsentTemplateVersionEntity version = consentService.getVersion(tenantId, consentVersionId);
		if (version.getStatus() != ConsentVersionStatus.PUBLISHED) {
			throw new IllegalStateException(
					"Solo se pueden generar documentos desde versiones PUBLISHED. " +
							"Estado actual: " + version.getStatus());
		}

		// 2. Cargar paciente + tenant
		PatientEntity patient = patientRepository.findById(patientId)
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));
		if (!patient.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado al paciente");
		}

		TenantEntity tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

		// 3. Renderizar con valores reales del paciente
		RenderContext ctx = buildContext(patient, tenant);
		RenderedConsentResponse rendered = consentRenderer.render(
				version.getId(), version.getVersionNumber(),
				version.getTitle(), version.getContentHtml(), ctx);

		// 4. Crear PatientDocument con snapshot
		PatientDocumentEntity doc = new PatientDocumentEntity();
		doc.setTenantId(tenantId);
		doc.setPatientId(patientId);
		doc.setConsentTemplateVersionId(version.getId());
		doc.setConsentTemplateId(version.getTemplateId());
		doc.setTemplateName(version.getTitle());
		doc.setTemplateVersionNumber(version.getVersionNumber());
		doc.setTitle(customTitle != null && !customTitle.isBlank()
				? customTitle : version.getTitle());
		doc.setRenderedHtml(rendered.renderedHtml());
		doc.setStatus("DRAFT");

		return documentRepository.save(doc);
	}

	// ─── READ ─────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public PatientDocumentEntity getById(UUID tenantId, UUID id) {
		return documentRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));
	}

	@Transactional(readOnly = true)
	public List<PatientDocumentEntity> listByPatient(UUID tenantId, UUID patientId) {
		return documentRepository.findByTenantIdAndPatientIdOrderByGeneratedAtDesc(tenantId, patientId);
	}

	// ─── UPDATE CONTENT (solo DRAFT) ──────────────────────────────────

	@Transactional
	public PatientDocumentEntity updateContent(UUID tenantId, UUID id, String renderedHtml, String title) {
		PatientDocumentEntity doc = getById(tenantId, id);
		if (!"DRAFT".equals(doc.getStatus())) {
			throw new IllegalStateException("Solo se pueden editar documentos en DRAFT.");
		}
		doc.setRenderedHtml(renderedHtml);
		if (title != null && !title.isBlank()) {
			doc.setTitle(title);
		}
		return documentRepository.save(doc);
	}

	// ─── PREPARE FOR SIGNING ──────────────────────────────────────────

	@Transactional
	public PatientDocumentEntity prepareForSigning(UUID tenantId, UUID id) {
		PatientDocumentEntity doc = getById(tenantId, id);

		if (!"DRAFT".equals(doc.getStatus()) && !"READY_TO_SIGN".equals(doc.getStatus())) {
			throw new IllegalStateException("El documento ya está firmado.");
		}

		byte[] pdfBytes = pdfRenderer.htmlToPdf(doc.getRenderedHtml(), doc.getTitle());
		String storageKey = storageService.storeBytes(
				tenantId, doc.getPatientId(), doc.getId(),
				pdfBytes, "application/pdf",
				"doc-" + doc.getId() + ".pdf"
				);

		doc.setPdfStorageKey(storageKey);
		doc.setStatus("READY_TO_SIGN");
		return documentRepository.save(doc);
	}

	// ─── ARCHIVE / DELETE ─────────────────────────────────────────────

	@Transactional
	public PatientDocumentEntity archive(UUID tenantId, UUID id) {
		PatientDocumentEntity doc = getById(tenantId, id);
		if (!"SIGNED".equals(doc.getStatus())) {
			throw new IllegalStateException("Solo se archivan documentos SIGNED.");
		}
		doc.setStatus("ARCHIVED");
		return documentRepository.save(doc);
	}

	@Transactional
	public void deleteDraft(UUID tenantId, UUID id) {
		PatientDocumentEntity doc = getById(tenantId, id);
		if (!"DRAFT".equals(doc.getStatus())) {
			throw new IllegalStateException("Solo se eliminan documentos DRAFT.");
		}
		documentRepository.delete(doc);
	}

	// ─── Helpers ──────────────────────────────────────────────────────

	/**
	 * Construye el RenderContext desde las entidades reales.
	 */
	private RenderContext buildContext(PatientEntity p, TenantEntity t) {
		String genderLabel = "M".equals(p.getGender()) ? "Masculino"
				: "F".equals(p.getGender()) ? "Femenino" : null;

		return new RenderContext(
				DEFAULT_ZONE,
				p.getFullName(),
				p.getFirstName(),
				p.getLastName(),
				p.getDocumentType(),
				p.getDocumentNumber(),
				p.getEmail(),
				p.getPhone(),
				p.getBirthDate(),
				genderLabel,
				p.getNationality(),
				p.getAddress(),
				p.getBloodType(),
				p.getAllergies(),
				p.getMedicalConditions(),
				p.getCurrentMedications(),
				p.getEmergencyContactName(),
				p.getEmergencyContactPhone(),
				p.getEmergencyContactRelation(),
				null, // appointment (Fase futura)
				null, null, // service (Fase futura)
				t != null ? t.getDisplayName() : null,
						t != null ? t.getAddress() : null,
								t != null ? t.getContactPhone() : null,
										t != null ? t.getContactEmail() : null
				);
	}

	public static String sha256(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(data);
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new RuntimeException("SHA-256 unavailable", e);
		}
	}
}
