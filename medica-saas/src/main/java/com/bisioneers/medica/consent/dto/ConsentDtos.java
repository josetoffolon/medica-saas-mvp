package com.bisioneers.medica.consent.dto;

import com.bisioneers.medica.consent.domain.ConsentVersionStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConsentDtos {

	private ConsentDtos() {}

	/** Límite generoso para contenido HTML de una plantilla (mejora #3). */
	public static final int MAX_HTML_SIZE = 100_000;

	// ─── Template (cabecera) ──────────────────────────────────────────

	public record CreateTemplateRequest(
			@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$",
			message = "code debe ser slug minúscula con guiones (ej: botox-frontal)")
			String code,
			@Size(max = 500) String description,
			Integer displayOrder,

			@NotBlank @Size(max = 300) String initialTitle,

			@NotBlank
			@Size(max = MAX_HTML_SIZE, message = "El HTML no puede exceder 100KB")
			String initialContentHtml
			) {}

	public record UpdateTemplateRequest(
			@Size(max = 200) String name,
			@Size(max = 500) String description,
			Integer displayOrder
			) {}

	public record TemplateResponse(
			UUID id,
			String name,
			String code,
			String description,
			UUID currentVersionId,
			Integer currentVersionNumber,
			boolean active,
			int displayOrder,
			long totalVersions
			) {}

	// ─── Versions ─────────────────────────────────────────────────────

	public record CreateVersionRequest(
			@NotBlank @Size(max = 300) String title,
			@NotBlank @Size(max = MAX_HTML_SIZE) String contentHtml
			) {}

	public record UpdateDraftVersionRequest(
			@Size(max = 300) String title,
			@Size(max = MAX_HTML_SIZE) String contentHtml
			) {}

	/** Resumen ligero para listings (mejora #9: sin contentHtml). */
	public record VersionSummaryResponse(
			UUID id,
			UUID templateId,
			int versionNumber,
			String title,
			ConsentVersionStatus status,
			Instant publishedAt,
			UUID publishedByUserId,
			Instant lastEditedAt,
			UUID lastEditedByUserId
			) {}

	/** Detalle completo con HTML (mejora #10). */
	public record VersionResponse(
			UUID id,
			UUID templateId,
			int versionNumber,
			String title,
			String contentHtml,
			ConsentVersionStatus status,
			Instant publishedAt,
			UUID publishedByUserId,
			Instant lastEditedAt,
			UUID lastEditedByUserId
			) {}

	// ─── Render preview ───────────────────────────────────────────────

	public record RenderPreviewRequest(
			UUID patientId,
			UUID appointmentId,
			UUID serviceId
			) {}

	public record RenderedConsentResponse(
			UUID versionId,
			int versionNumber,
			String title,
			String renderedHtml,
			List<String> unresolvedVariables
			) {}

	// ─── Variable catalog (introspección para el editor) ──────────────

	public record VariableDescriptor(
			String token,
			String label,
			String category
			) {}
}
