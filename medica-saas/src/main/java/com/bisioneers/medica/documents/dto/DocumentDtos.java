package com.bisioneers.medica.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs del módulo documents (instancias firmables).
 *
 * REFACTOR: las plantillas ahora viven en el módulo `consent`.
 * Aquí solo manejamos el ciclo de vida del documento del paciente.
 */
public final class DocumentDtos {

	private DocumentDtos() {}

	/**
	 * Para generar un documento desde una versión PUBLISHED de consent.
	 */
	public record GenerateDocumentRequest(
			@NotNull UUID patientId,
			@NotNull UUID consentTemplateVersionId,
			UUID appointmentId,   // opcional para resolver {{appointment.*}}
			UUID serviceId,       // opcional para resolver {{service.*}}
			String title          // opcional para personalizar título
			) {}

	public record UpdateDocumentContentRequest(
			@NotBlank @Size(max = 100_000) String renderedHtml,
			String title
			) {}

	public record DocumentResponse(
			UUID id,
			UUID patientId,
			UUID consentTemplateVersionId,
			UUID consentTemplateId,
			String templateName,
			Integer templateVersionNumber,
			String title,
			String renderedHtml,
			String status,
			String pdfUrl,
			String signedPdfUrl,
			String signatureMethod,
			String integrityHash,
			Instant generatedAt,
			Instant signedAt,
			String signerName
			) {}

	public record DocumentSummaryResponse(
			UUID id,
			UUID patientId,
			String templateName,
			Integer templateVersionNumber,
			String title,
			String status,
			String signatureMethod,
			Instant generatedAt,
			Instant signedAt
			) {}
}
