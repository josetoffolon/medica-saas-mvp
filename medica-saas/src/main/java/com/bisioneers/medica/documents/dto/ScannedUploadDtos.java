package com.bisioneers.medica.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs para la subida de documentos firmados físicamente (escaneados).
 *
 * El flujo SCANNED es para cuando el paciente firma en papel y la clínica
 * digitaliza el documento. Se captura el mismo set de datos del firmante que
 * en la firma digital, pero el "comprobante" es el archivo subido en vez de
 * una firma de canvas.
 */
public final class ScannedUploadDtos {

	private ScannedUploadDtos() {}

	/**
	 * Metadatos que acompañan al archivo subido (multipart).
	 * El archivo en sí va como MultipartFile aparte.
	 */
	public record UploadScannedRequest(
			@NotBlank @Size(max = 250) String signerName,
			@NotBlank @Size(max = 50) String signerDocument,
			/** Fecha de la firma física en formato ISO (yyyy-MM-dd), opcional. */
			@Size(max = 10) String signedDate
			) {}
}
