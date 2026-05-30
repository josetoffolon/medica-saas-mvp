package com.bisioneers.medica.documents.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.documents.domain.PatientDocumentEntity;
import com.bisioneers.medica.documents.domain.SignatureRequestEntity;
import com.bisioneers.medica.documents.dto.SignatureDtos.*;
import com.bisioneers.medica.documents.service.SignatureService;
import com.bisioneers.medica.documents.service.SignatureService.RemoteSignatureCreationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints autenticados para iniciar y confirmar firmas de documentos.
 *
 *  POST /api/documents/{id}/sign-request/in-person   → iniciar flujo IN_PERSON
 *  POST /api/documents/{id}/sign-request/remote      → iniciar flujo REMOTE (con canal)
 *  POST /api/documents/{id}/sign/confirm             → confirmar firma IN_PERSON
 *  GET  /api/documents/{id}/sign-requests            → historial de solicitudes
 *  POST /api/sign-requests/{id}/cancel               → cancelar solicitud PENDING
 */
@RestController
@RequestMapping("/api")
public class SignatureController {

	private final SignatureService signatureService;

	public SignatureController(SignatureService signatureService) {
		this.signatureService = signatureService;
	}

	// ─── IN_PERSON ────────────────────────────────────────────────

	@PostMapping("/documents/{id}/sign-request/in-person")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'RECEPCION')")
	public ResponseEntity<SignatureRequestResponse> startInPerson(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		SignatureRequestEntity req = signatureService.startInPersonSignature(
				principal.getTenantId(), id, principal.getUserId());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(req, null, null));
	}

	@PostMapping("/documents/{id}/sign/confirm")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'RECEPCION')")
	public ResponseEntity<Map<String, Object>> confirmInPerson(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody ConfirmInPersonSignRequest req,
			HttpServletRequest httpReq
			) {
		String clientIp = extractClientIp(httpReq);
		String userAgent = httpReq.getHeader("User-Agent");

		PatientDocumentEntity signed = signatureService.confirmInPersonSignature(
				principal.getTenantId(), id, req.signatureRequestId(),
				req.signatureDataUrl(), req.signerName(), req.signerDocument(),
				clientIp, userAgent, principal.getUserId()
				);

		return ResponseEntity.ok(Map.of(
				"message", "Documento firmado correctamente",
				"documentId", signed.getId(),
				"integrityHash", signed.getIntegrityHash(),
				"signedAt", signed.getSignedAt()
				));
	}

	// ─── REMOTE ───────────────────────────────────────────────────

	@PostMapping("/documents/{id}/sign-request/remote")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'RECEPCION')")
	public ResponseEntity<SignatureRequestResponse> startRemote(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@Valid @RequestBody StartRemoteSignRequest req
			) {
		RemoteSignatureCreationResult result = signatureService.startRemoteSignature(
				principal.getTenantId(), id, principal.getUserId(),
				req.channel(), req.customMessage());

		// Devolvemos el token plano ESTA SOLA VEZ
		return ResponseEntity.status(HttpStatus.CREATED).body(
				toResponse(result.request(), result.tokenPlain(), result.signUrl()));
	}

	// ─── LIST / CANCEL ────────────────────────────────────────────

	@GetMapping("/documents/{id}/sign-requests")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'RECEPCION')")
	public ResponseEntity<List<SignatureRequestResponse>> list(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id
			) {
		List<SignatureRequestResponse> resp = signatureService
				.listByDocument(principal.getTenantId(), id).stream()
				.map(r -> toResponse(r, null, null))
				.toList();
		return ResponseEntity.ok(resp);
	}

	@PostMapping("/sign-requests/{requestId}/cancel")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'RECEPCION')")
	public ResponseEntity<Map<String, String>> cancel(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID requestId
			) {
		signatureService.cancelRequest(principal.getTenantId(), requestId);
		return ResponseEntity.ok(Map.of("message", "Solicitud cancelada"));
	}

	// ─── Mapper ───────────────────────────────────────────────────

	private SignatureRequestResponse toResponse(SignatureRequestEntity r,
			String tokenPlain, String signUrl) {
		return new SignatureRequestResponse(
				r.getId(),
				r.getPatientDocumentId(),
				r.getMethod(),
				r.getStatus(),
				tokenPlain,
				signUrl,
				r.getDeliveryChannel(),
				r.getDeliveryTarget(),
				r.getDeliverySuccessful(),
				r.getDeliveryError(),
				r.getFailedAttempts(),
				r.getExpiresAt(),
				r.getSignedAt(),
				r.getCreatedByStaffUserId(),
				r.getWitnessStaffUserId()
				);
	}

	/**
	 * Extrae la IP del cliente respetando X-Forwarded-For (Nginx + Cloudflare).
	 * Toma siempre la primera IP (la del cliente, no la de proxies).
	 */
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

