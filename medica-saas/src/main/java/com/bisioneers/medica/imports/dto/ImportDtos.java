package com.bisioneers.medica.imports.dto;

import com.bisioneers.medica.imports.domain.DuplicateStrategy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs para el módulo de importación de pacientes. */
public final class ImportDtos {

	private ImportDtos() {}

	// ─── Requests ─────────────────────────────────────────────────────

	public record CommitRequest(
			DuplicateStrategy duplicateStrategy   // null → SKIP
			) {}

	// ─── Responses ────────────────────────────────────────────────────

	/** Resumen de un lote (para pantalla de preview y detalle). */
	public record BatchSummaryResponse(
			UUID id,
			String fileName,
			String status,
			int totalRows,
			int okRows,
			int warningRows,
			int errorRows,
			int duplicateRows,
			int importedRows,
			int skippedRows,
			String errorMessage,
			Instant createdAt,
			Instant committedAt,
			Instant revertedAt
			) {}

	/** Una fila para el preview (raw + normalizado + mensajes). */
	public record RowResponse(
			UUID id,
			int rowNumber,
			String status,
			List<String> messages,
			String matchReason,
			UUID matchPatientId,
			UUID patientId,
			// campos normalizados clave para mostrar en la tabla de preview
			String firstName,
			String lastName,
			String phone,
			String email,
			String documentNumber
			) {}
}