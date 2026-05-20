package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.documents.domain.DocumentTemplateEntity;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestión del ciclo de vida de documentos del paciente.
 *
 * Flujo de estados:
 *   1. generate() → crea documento en DRAFT (HTML editable)
 *   2. prepareForSigning() → renderiza PDF base, estado READY_TO_SIGN
 *   3. signDigital() / uploadSigned() → almacena PDF firmado, estado SIGNED
 */
@Service
public class PatientDocumentService {

	private static final Logger log = LoggerFactory.getLogger(PatientDocumentService.class);

	private final PatientDocumentRepository documentRepository;
	private final DocumentTemplateService templateService;
	private final PatientRepository patientRepository;
	private final TenantRepository tenantRepository;
	private final MergeFieldEngine mergeEngine;
	private final PdfRenderer pdfRenderer;
	private final MediaStorageService storageService;

	public PatientDocumentService(PatientDocumentRepository documentRepository,
			DocumentTemplateService templateService,
			PatientRepository patientRepository,
			TenantRepository tenantRepository,
			MergeFieldEngine mergeEngine,
			PdfRenderer pdfRenderer,
			MediaStorageService storageService) {
		this.documentRepository = documentRepository;
		this.templateService = templateService;
		this.patientRepository = patientRepository;
		this.tenantRepository = tenantRepository;
		this.mergeEngine = mergeEngine;
		this.pdfRenderer = pdfRenderer;
		this.storageService = storageService;
	}

	// ─── GENERATE (DRAFT) ─────────────────────────────────────────────

	/**
	 * Genera un documento DRAFT desde una plantilla y un paciente.
	 * Aplica los merge fields y guarda el HTML renderizado como snapshot.
	 */
	@Transactional
	public PatientDocumentEntity generate(UUID tenantId, UUID patientId,
			UUID templateId, String customTitle) {

		PatientEntity patient = patientRepository.findById(patientId)
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));
		if (!patient.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado al paciente");
		}

		TenantEntity tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

		DocumentTemplateEntity template = templateService.getById(tenantId, templateId);

		String rendered = mergeEngine.render(template.getContentHtml(), patient, tenant);

		PatientDocumentEntity doc = new PatientDocumentEntity();
		doc.setTenantId(tenantId);
		doc.setPatientId(patientId);
		doc.setTemplateId(templateId);
		doc.setTemplateName(template.getName());
		doc.setDocumentType(template.getDocumentType());
		doc.setTitle(customTitle != null && !customTitle.isBlank()
				? customTitle : template.getName());
		doc.setRenderedHtml(rendered);
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

	// ─── UPDATE (solo DRAFT) ──────────────────────────────────────────

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

	/**
	 * Genera el PDF base desde el HTML actual y lo guarda en R2.
	 * Cambia el estado a READY_TO_SIGN.
	 */
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

	// ─── ARCHIVE ──────────────────────────────────────────────────────

	@Transactional
	public PatientDocumentEntity archive(UUID tenantId, UUID id) {
		PatientDocumentEntity doc = getById(tenantId, id);
		if (!"SIGNED".equals(doc.getStatus())) {
			throw new IllegalStateException("Solo se archivan documentos SIGNED.");
		}
		doc.setStatus("ARCHIVED");
		return documentRepository.save(doc);
	}

	// ─── DELETE (solo DRAFT) ──────────────────────────────────────────

	@Transactional
	public void deleteDraft(UUID tenantId, UUID id) {
		PatientDocumentEntity doc = getById(tenantId, id);
		if (!"DRAFT".equals(doc.getStatus())) {
			throw new IllegalStateException("Solo se eliminan documentos DRAFT.");
		}
		documentRepository.delete(doc);
	}

	// ─── Hashing (para uso en Fase 3 cuando se firme) ─────────────────

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
