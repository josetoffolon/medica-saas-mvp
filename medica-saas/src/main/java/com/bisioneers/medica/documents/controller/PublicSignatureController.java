package com.bisioneers.medica.documents.controller;

import com.bisioneers.medica.documents.domain.PatientDocumentEntity;
import com.bisioneers.medica.documents.domain.PatientDocumentRepository;
import com.bisioneers.medica.documents.domain.SignatureRequestEntity;
import com.bisioneers.medica.documents.dto.SignatureDtos.*;
import com.bisioneers.medica.documents.service.SignatureService;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints PÚBLICOS para que el paciente firme remotamente.
 *
 * NO requieren autenticación JWT. El token de la URL es el único mecanismo
 * de autorización.
 *
 * IMPORTANTE: estos endpoints deben estar whitelisted en:
 *   - SecurityConfig (permitir /api/public/** sin auth)
 *   - SubscriptionEnforcementFilter (ya estaba en WHITELIST_PREFIXES con /api/public)
 *   - TenantAwareTransactionManager (si filtra por tenant; aquí lo resuelve el token)
 */
@RestController
@RequestMapping("/api/public/sign")
public class PublicSignatureController {

	private final SignatureService signatureService;
	private final PatientDocumentRepository documentRepo;
	private final PatientRepository patientRepo;
	private final TenantRepository tenantRepo;

	public PublicSignatureController(SignatureService signatureService,
			PatientDocumentRepository documentRepo,
			PatientRepository patientRepo,
			TenantRepository tenantRepo) {
		this.signatureService = signatureService;
		this.documentRepo = documentRepo;
		this.patientRepo = patientRepo;
		this.tenantRepo = tenantRepo;
	}

	/**
	 * GET /api/public/sign/{token}
	 *
	 * El paciente abre el link → frontend llama este endpoint.
	 * Si el token es válido: devuelve datos del documento (HTML renderizado,
	 * iniciales del paciente, hint del documento) pero NO datos sensibles
	 * (no nombre completo, no dirección, no historial).
	 */
	@GetMapping("/{token}")
	public ResponseEntity<PublicSignViewResponse> viewDocument(@PathVariable String token) {
		SignatureRequestEntity req = signatureService.resolveTokenForView(token);

		PatientDocumentEntity doc = documentRepo.findById(req.getPatientDocumentId())
				.orElseThrow(() -> new IllegalStateException("Documento no encontrado"));
		PatientEntity patient = patientRepo.findById(doc.getPatientId())
				.orElseThrow(() -> new IllegalStateException("Paciente no encontrado"));
		TenantEntity tenant = tenantRepo.findById(req.getTenantId()).orElse(null);

		// Datos mínimos para que el paciente confirme que es su documento
		String initials = buildInitials(patient);
		String docHint = buildDocumentHint(patient.getDocumentNumber());

		return ResponseEntity.ok(new PublicSignViewResponse(
				req.getId(),
				doc.getId(),
				doc.getTitle(),
				doc.getRenderedHtml(),
				initials,
				docHint,
				tenant != null ? tenant.getDisplayName() : "Clinica",
						req.getExpiresAt()
				));
	}

	/**
	 * POST /api/public/sign/{token}
	 *
	 * El paciente envía: firma PNG base64 + número de documento como challenge.
	 * Si pasa la validación, finaliza la firma.
	 */
	@PostMapping("/{token}")
	public ResponseEntity<Map<String, Object>> submitSignature(
			@PathVariable String token,
			@Valid @RequestBody SubmitRemoteSignRequest req,
			HttpServletRequest httpReq
			) {
		String clientIp = extractClientIp(httpReq);
		String userAgent = httpReq.getHeader("User-Agent");

		PatientDocumentEntity signed = signatureService.confirmRemoteSignature(
				token, req.signatureDataUrl(), req.confirmDocumentNumber(),
				clientIp, userAgent
				);

		return ResponseEntity.ok(Map.of(
				"message", "Documento firmado correctamente. Gracias.",
				"signedAt", signed.getSignedAt()
				));
	}

	// ─── Helpers ──────────────────────────────────────────────────

	/**
	 * Iniciales del paciente para confirmación visual.
	 * Ej: "María González Pérez" → "M.G."
	 */
	private String buildInitials(PatientEntity p) {
		String first = p.getFirstName() != null && !p.getFirstName().isBlank()
				? String.valueOf(p.getFirstName().charAt(0)).toUpperCase() : "?";
		String last = p.getLastName() != null && !p.getLastName().isBlank()
				? String.valueOf(p.getLastName().charAt(0)).toUpperCase() : "?";
		return first + "." + last + ".";
	}

	/**
	 * Hint del número de documento — últimos 3 dígitos.
	 * Ej: "8-123-456" → "•••-456"
	 */
	private String buildDocumentHint(String doc) {
		if (doc == null || doc.length() < 3) return "•••";
		String last = doc.substring(doc.length() - 3);
		return "•••-" + last;
	}

	private String extractClientIp(HttpServletRequest req) {
		String xff = req.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}
		String xri = req.getHeader("X-Real-IP");
		if (xri != null && !xri.isBlank()) return xri.trim();
		return req.getRemoteAddr();
	}
}
