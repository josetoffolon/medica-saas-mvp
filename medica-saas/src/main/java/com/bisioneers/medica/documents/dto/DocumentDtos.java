package com.bisioneers.medica.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

	private DocumentDtos() {}

	// ═══════════════════════════════════════════════════════════════════
	// TEMPLATES
	// ═══════════════════════════════════════════════════════════════════

	public record CreateTemplateRequest(
			@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 50) String documentType,
			@NotBlank String contentHtml,
			@Size(max = 500) String description
			) {}

	public record UpdateTemplateRequest(
			@NotBlank @Size(max = 200) String name,
			@NotBlank String contentHtml,
			@Size(max = 500) String description
			) {}

	public record TemplateResponse(
			UUID id,
			String name,
			String documentType,
			String contentHtml,
			String description,
			int version,
			boolean active,
			boolean isSystem
			) {}

	public record TemplateSummaryResponse(
			UUID id,
			String name,
			String documentType,
			String description,
			int version,
			boolean isSystem
			) {}

	// ═══════════════════════════════════════════════════════════════════
	// PATIENT DOCUMENTS
	// ═══════════════════════════════════════════════════════════════════

	public record GenerateDocumentRequest(
			@NotNull UUID patientId,
			@NotNull UUID templateId,
			String title
			) {}

	public record UpdateDocumentContentRequest(
			@NotBlank String renderedHtml,
			String title
			) {}

	public record DocumentResponse(
			UUID id,
			UUID patientId,
			UUID templateId,
			String templateName,
			String documentType,
			String title,
			String renderedHtml,
			String status,
			String pdfUrl,                  // presigned URL (5 min) or null
			String signedPdfUrl,            // presigned URL (5 min) or null
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
			String documentType,
			String title,
			String status,
			String signatureMethod,
			Instant generatedAt,
			Instant signedAt
			) {}
}
