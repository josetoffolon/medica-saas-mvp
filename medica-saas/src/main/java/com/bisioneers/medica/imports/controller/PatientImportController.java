package com.bisioneers.medica.imports.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.imports.domain.DuplicateStrategy;
import com.bisioneers.medica.imports.domain.ImportRowStatus;
import com.bisioneers.medica.imports.domain.PatientImportBatchEntity;
import com.bisioneers.medica.imports.domain.PatientImportRowEntity;
import com.bisioneers.medica.imports.dto.ImportDtos.*;
import com.bisioneers.medica.imports.service.PatientImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Importación masiva de pacientes desde CSV. Todo restringido a ADMIN:
 * importar la base de pacientes es una operación de dueño de clínica.
 *
 *   GET    /api/patients/import/template          → CSV plantilla
 *   POST   /api/patients/import/analyze           → analizar (no escribe)
 *   GET    /api/patients/import                   → historial de lotes
 *   GET    /api/patients/import/{id}              → resumen de un lote
 *   GET    /api/patients/import/{id}/rows         → filas (paginadas, filtrables)
 *   GET    /api/patients/import/{id}/errors.csv   → filas con problema
 *   POST   /api/patients/import/{id}/commit       → confirmar alta en lote
 *   POST   /api/patients/import/{id}/revert       → deshacer lote
 */
@RestController
@RequestMapping("/api/patients/import")
@PreAuthorize("hasRole('ADMIN')")
public class PatientImportController {

	private final PatientImportService importService;
	private final ObjectMapper objectMapper;

	public PatientImportController(PatientImportService importService,
			ObjectMapper objectMapper) {
		this.importService = importService;
		this.objectMapper = objectMapper;
	}

	// ─── Plantilla ────────────────────────────────────────────────────

	@GetMapping("/template")
	public ResponseEntity<byte[]> template() {
		byte[] body = importService.buildTemplate().getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"plantilla_pacientes.csv\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(body);
	}

	// ─── Analyze ──────────────────────────────────────────────────────

	@PostMapping(value = "/analyze", consumes = "multipart/form-data")
	public ResponseEntity<BatchSummaryResponse> analyze(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@RequestParam("file") MultipartFile file) {

		PatientImportBatchEntity batch = importService.analyze(principal.getTenantId(), file);
		return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(batch));
	}

	// ─── Read ─────────────────────────────────────────────────────────

	@GetMapping
	public ResponseEntity<List<BatchSummaryResponse>> list(
			@AuthenticationPrincipal StaffUserPrincipal principal) {
		List<BatchSummaryResponse> body = importService.listBatches(principal.getTenantId())
				.stream().map(this::toSummary).toList();
		return ResponseEntity.ok(body);
	}

	@GetMapping("/{id}")
	public ResponseEntity<BatchSummaryResponse> getBatch(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {
		return ResponseEntity.ok(toSummary(importService.getBatch(principal.getTenantId(), id)));
	}

	@GetMapping("/{id}/rows")
	public ResponseEntity<Page<RowResponse>> rows(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@RequestParam(required = false) ImportRowStatus status,
			@PageableDefault(size = 50) Pageable pageable) {

		Page<PatientImportRowEntity> page =
				importService.getRows(principal.getTenantId(), id, status, pageable);
		return ResponseEntity.ok(page.map(this::toRow));
	}

	/** CSV solo con filas ERROR/WARNING/DUPLICATE para corregir y re-subir. */
	@GetMapping("/{id}/errors.csv")
	public ResponseEntity<byte[]> errorsCsv(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {

		UUID tenantId = principal.getTenantId();
		StringBuilder sb = new StringBuilder("fila,estado,mensajes,nombre,apellido,telefono,email,cedula\n");

		for (ImportRowStatus st : List.of(
				ImportRowStatus.ERROR, ImportRowStatus.WARNING, ImportRowStatus.DUPLICATE)) {
			Page<PatientImportRowEntity> page = importService.getRows(
					tenantId, id, st, Pageable.ofSize(1000));
			for (PatientImportRowEntity r : page.getContent()) {
				RowResponse rr = toRow(r);
				sb.append(csv(String.valueOf(rr.rowNumber())))
				.append(',').append(csv(rr.status()))
				.append(',').append(csv(String.join(" | ", rr.messages())))
				.append(',').append(csv(rr.firstName()))
				.append(',').append(csv(rr.lastName()))
				.append(',').append(csv(rr.phone()))
				.append(',').append(csv(rr.email()))
				.append(',').append(csv(rr.documentNumber()))
				.append('\n');
			}
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"filas_con_problema.csv\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	// ─── Commit / Revert ──────────────────────────────────────────────

	@PostMapping("/{id}/commit")
	public ResponseEntity<BatchSummaryResponse> commit(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id,
			@RequestBody(required = false) CommitRequest request) {

		DuplicateStrategy strategy = (request != null) ? request.duplicateStrategy() : null;
		PatientImportBatchEntity batch =
				importService.commit(principal.getTenantId(), id, strategy);
		return ResponseEntity.ok(toSummary(batch));
	}

	@PostMapping("/{id}/revert")
	public ResponseEntity<BatchSummaryResponse> revert(
			@AuthenticationPrincipal StaffUserPrincipal principal,
			@PathVariable UUID id) {
		PatientImportBatchEntity batch =
				importService.revert(principal.getTenantId(), id);
		return ResponseEntity.ok(toSummary(batch));
	}

	// ─── Mappers ──────────────────────────────────────────────────────

	private BatchSummaryResponse toSummary(PatientImportBatchEntity b) {
		return new BatchSummaryResponse(
				b.getId(), b.getFileName(), b.getStatus().name(),
				b.getTotalRows(), b.getOkRows(), b.getWarningRows(),
				b.getErrorRows(), b.getDuplicateRows(), b.getImportedRows(),
				b.getSkippedRows(), b.getErrorMessage(),
				b.getCreatedAt(), b.getCommittedAt(), b.getRevertedAt());
	}

	private RowResponse toRow(PatientImportRowEntity r) {
		Map<String, Object> nd = readMap(r.getNormalizedData());
		List<String> msgs = readList(r.getMessages());
		return new RowResponse(
				r.getId(), r.getRowNumber(), r.getStatus().name(), msgs,
				r.getMatchReason(), r.getMatchPatientId(), r.getPatientId(),
				strv(nd, "firstName"), strv(nd, "lastName"), strv(nd, "phone"),
				strv(nd, "email"), strv(nd, "documentNumber"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readMap(String json) {
		if (json == null || json.isBlank()) return Map.of();
		try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
		catch (Exception e) { return Map.of(); }
	}

	private List<String> readList(String json) {
		if (json == null || json.isBlank()) return List.of();
		try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
		catch (Exception e) { return List.of(); }
	}

	private static String strv(Map<String, Object> m, String k) {
		Object v = m.get(k);
		return v == null ? null : v.toString();
	}

	/** Escapa un campo para CSV (comillas si contiene , " o salto). */
	private static String csv(String v) {
		if (v == null) return "";
		if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
			return "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}
}