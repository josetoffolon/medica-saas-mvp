package com.bisioneers.medica.documents.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.documents.domain.PatientDocumentEntity;
import com.bisioneers.medica.documents.service.ScannedDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint para subir documentos firmados físicamente (escaneados).
 *
 *  POST /api/documents/{id}/upload-signed   (multipart/form-data)
 *    - file: el PDF o imagen escaneada
 *    - signerName: nombre del firmante
 *    - signerDocument: cédula/pasaporte
 *    - signedDate: (opcional) fecha de firma física, ISO yyyy-MM-dd
 *
 * El staff autenticado queda como testigo (witnessStaffUserId).
 */
@RestController
@RequestMapping("/api/documents")
public class ScannedDocumentController {

	private final ScannedDocumentService scannedService;

	public ScannedDocumentController(ScannedDocumentService scannedService) {
		this.scannedService = scannedService;
	}

	@PostMapping(value = "/{id}/upload-signed", consumes = "multipart/form-data")
	@PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'RECEPCION')")
	public ResponseEntity<Map<String, Object>> uploadSigned(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@RequestParam("file") MultipartFile file,
			@RequestParam("signerName") @NotBlank String signerName,
			@RequestParam("signerDocument") @NotBlank String signerDocument,
			@RequestParam(value = "signedDate", required = false) String signedDate,
			HttpServletRequest httpReq
			) {
		String clientIp = extractClientIp(httpReq);
		String userAgent = httpReq.getHeader("User-Agent");

		PatientDocumentEntity signed = scannedService.uploadScanned(
				principal.getTenantId(), id, file,
				signerName.trim(), signerDocument.trim(), signedDate,
				clientIp, userAgent, principal.getUserId()
				);

		return ResponseEntity.ok(Map.of(
				"message", "Documento escaneado subido correctamente",
				"documentId", signed.getId(),
				"integrityHash", signed.getIntegrityHash(),
				"signedAt", signed.getSignedAt(),
				"signatureMethod", signed.getSignatureMethod()
				));
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
