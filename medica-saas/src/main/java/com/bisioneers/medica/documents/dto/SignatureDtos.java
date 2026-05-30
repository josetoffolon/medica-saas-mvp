package com.bisioneers.medica.documents.dto;

import com.bisioneers.medica.documents.domain.RemoteDeliveryChannel;
import com.bisioneers.medica.documents.domain.SignatureMethod;
import com.bisioneers.medica.documents.domain.SignatureRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class SignatureDtos {

	private SignatureDtos() {}

	// ─── Requests del staff ────────────────────────────────────

	/**
	 * Para iniciar firma in-situ (no requiere body).
	 * El staff y paciente firmarán en el mismo dispositivo.
	 */
	public record StartInPersonSignRequest() {}

	/**
	 * Para iniciar firma remota.
	 */
	public record StartRemoteSignRequest(
			@NotNull RemoteDeliveryChannel channel,
			@Size(max = 200) String customMessage
			) {}

	/**
	 * Para confirmar firma in-situ (staff autenticado).
	 */
	public record ConfirmInPersonSignRequest(
			@NotNull UUID signatureRequestId,
			/** PNG base64 dataURL: "data:image/png;base64,iVBOR..." o solo base64 */
			@NotBlank String signatureDataUrl,
			@NotBlank @Size(max = 250) String signerName,
			@NotBlank @Size(max = 50) String signerDocument
			) {}

	// ─── Requests del paciente (endpoint público) ──────────────

	public record SubmitRemoteSignRequest(
			@NotBlank String signatureDataUrl,
			/** El paciente DEBE confirmar su documento como challenge */
			@NotBlank @Size(max = 50) String confirmDocumentNumber
			) {}

	// ─── Responses ─────────────────────────────────────────────

	public record SignatureRequestResponse(
			UUID id,
			UUID patientDocumentId,
			SignatureMethod method,
			SignatureRequestStatus status,
			/** Solo se devuelve UNA vez al crear; en lecturas posteriores queda null */
			String tokenForPatient,
			/** URL completa lista para enviar al paciente */
			String signUrl,
			RemoteDeliveryChannel deliveryChannel,
			String deliveryTarget,
			Boolean deliverySuccessful,
			String deliveryError,
			int failedAttempts,
			Instant expiresAt,
			Instant signedAt,
			UUID createdByStaffUserId,
			UUID witnessStaffUserId
			) {}

	/**
	 * Lo que ve el paciente al abrir el link (endpoint público).
	 * NO incluye datos sensibles del paciente (excepto los enmascarados).
	 */
	public record PublicSignViewResponse(
			UUID signatureRequestId,
			UUID documentId,
			String documentTitle,
			/** HTML renderizado del documento que va a firmar */
			String renderedHtml,
			/** Iniciales del paciente para confirmar a quién pertenece (ej: "M.G.") */
			String patientInitials,
			/** Últimos 3 dígitos del documento como hint (ej: "...456") */
			String documentNumberHint,
			String tenantDisplayName,
			Instant expiresAt
			) {}
}
