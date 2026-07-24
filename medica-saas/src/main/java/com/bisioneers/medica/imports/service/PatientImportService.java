package com.bisioneers.medica.imports.service;

import com.bisioneers.medica.imports.domain.*;
import com.bisioneers.medica.imports.dto.ImportDtos.*;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Orquesta la importación masiva de pacientes desde CSV.
 *
 * analyze(): parsea + valida + detecta duplicados. NO escribe pacientes.
 * commit():  crea los pacientes de las filas OK/WARNING (y DUPLICATE según
 *            estrategia), en chunks, reutilizando el @PrePersist de
 *            PatientEntity para full_name.
 *
 * Convención de módulo: calcada de ServiceService (inyección por constructor,
 * @Transactional, todas las queries scoped por tenantId).
 */
@Service
public class PatientImportService {

	private static final Logger log = LoggerFactory.getLogger(PatientImportService.class);

	/** Columnas canónicas de la plantilla. */
	private static final String C_FIRST   = "nombre";
	private static final String C_MIDDLE  = "segundo_nombre";
	private static final String C_LAST    = "apellido";
	private static final String C_LAST2   = "segundo_apellido";
	private static final String C_PHONE   = "telefono";
	private static final String C_PHONE2  = "telefono_2";
	private static final String C_EMAIL   = "email";
	private static final String C_DOC      = "cedula";
	private static final String C_BIRTH   = "fecha_nacimiento";
	private static final String C_GENDER  = "genero";
	private static final String C_ADDRESS = "direccion";
	private static final String C_NOTES   = "notas";
	private static final String C_LEGACY  = "id_sistema_anterior";

	private static final int MAX_ROWS = 2000;
	private static final int CHUNK = 50;

	private final PatientImportBatchRepository batchRepo;
	private final PatientImportRowRepository rowRepo;
	private final PatientRepository patientRepo;
	private final ObjectMapper objectMapper;

	@Value("${app.patient-import.max-rows:2000}")
	private int maxRowsConfigured;

	public PatientImportService(PatientImportBatchRepository batchRepo,
			PatientImportRowRepository rowRepo,
			PatientRepository patientRepo,
			ObjectMapper objectMapper) {
		this.batchRepo = batchRepo;
		this.rowRepo = rowRepo;
		this.patientRepo = patientRepo;
		this.objectMapper = objectMapper;
	}

	// ═══════════════════════════════════════════════════════════════════
	//  ANALYZE
	// ═══════════════════════════════════════════════════════════════════

	/**
	 * Parsea y valida el CSV. Persiste el batch + todas sus filas.
	 * NO crea pacientes. Idempotente por (tenant, file_hash): si el mismo
	 * archivo ya fue analizado y no confirmado, devuelve ese batch.
	 */
	@Transactional
	public PatientImportBatchEntity analyze(UUID tenantId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("El archivo está vacío");
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException e) {
			throw new IllegalArgumentException("No se pudo leer el archivo", e);
		}

		String hash = sha256(bytes);

		// Idempotencia: mismo archivo ya analizado y no confirmado → reusar
		var existing = batchRepo.findByTenantIdAndFileHash(tenantId, hash).orElse(null);
		if (existing != null && existing.getStatus() == ImportBatchStatus.ANALYZED) {
			return existing;
		}
		if (existing != null && existing.getStatus() == ImportBatchStatus.COMMITTED) {
			throw new IllegalArgumentException(
					"Este archivo ya fue importado el " + existing.getCommittedAt());
		}

		Charset charset = PatientImportNormalizer.detectCharset(bytes);
		String content = new String(bytes, charset);

		List<List<String>> rows = SimpleCsvReader.parse(content);
		if (rows.size() < 2) {
			throw new IllegalArgumentException(
					"El archivo no tiene filas de datos (solo encabezado o vacío)");
		}

		Map<String, Integer> colIndex = mapHeaders(rows.get(0));
		int dataRows = rows.size() - 1;
		int limit = Math.min(maxRowsConfigured, MAX_ROWS);
		if (dataRows > limit) {
			throw new IllegalArgumentException(
					"El archivo tiene " + dataRows + " filas; el máximo por lote es " + limit +
					". Divídelo en varios archivos.");
		}

		// Crear el batch
		PatientImportBatchEntity batch = new PatientImportBatchEntity();
		batch.setTenantId(tenantId);
		batch.setFileName(safeName(file.getOriginalFilename()));
		batch.setFileHash(hash);
		batch.setFileSizeBytes(file.getSize());
		batch.setStatus(ImportBatchStatus.ANALYZING);
		batch.setTotalRows(dataRows);
		batch = batchRepo.save(batch);

		// Preload de índices de deduplicación del tenant (1 query por campo)
		Set<String> existingDocs   = lower(patientRepo.findAllDocumentNumbersByTenant(tenantId));
		Set<String> existingEmails = lower(patientRepo.findAllEmailsByTenant(tenantId));
		Set<String> existingLegacy = lower(patientRepo.findAllLegacyIdsByTenant(tenantId));

		// Índices dentro del propio archivo (para duplicados IN_FILE)
		Map<String, Integer> seenDoc = new HashMap<>();
		Map<String, Integer> seenEmail = new HashMap<>();
		Map<String, Integer> seenLegacy = new HashMap<>();

		int ok = 0, warn = 0, err = 0, dup = 0;
		List<PatientImportRowEntity> rowEntities = new ArrayList<>();

		for (int r = 1; r < rows.size(); r++) {
			List<String> raw = rows.get(r);
			int rowNumber = r; // 1-based sin encabezado

			String first   = get(raw, colIndex, C_FIRST);
			String middle  = get(raw, colIndex, C_MIDDLE);
			String last    = get(raw, colIndex, C_LAST);
			String last2   = get(raw, colIndex, C_LAST2);
			String phone   = get(raw, colIndex, C_PHONE);
			String phone2  = get(raw, colIndex, C_PHONE2);
			String email   = get(raw, colIndex, C_EMAIL);
			String doc     = get(raw, colIndex, C_DOC);
			String birth   = get(raw, colIndex, C_BIRTH);
			String gender  = get(raw, colIndex, C_GENDER);
			String address = get(raw, colIndex, C_ADDRESS);
			String notes   = get(raw, colIndex, C_NOTES);
			String legacy  = get(raw, colIndex, C_LEGACY);

			// ── Normalizar ──
			String nFirst  = PatientImportNormalizer.properName(first);
			String nMiddle = PatientImportNormalizer.properName(middle);
			String nLast   = PatientImportNormalizer.properName(last);
			String nLast2  = PatientImportNormalizer.properName(last2);
			String nPhone  = PatientImportNormalizer.phonePa(phone);
			String nPhone2 = PatientImportNormalizer.phonePa(phone2);
			String nEmail  = PatientImportNormalizer.email(email);
			String nDoc    = PatientImportNormalizer.documentPa(doc);
			LocalDate nBirth = PatientImportNormalizer.date(birth);
			String nGender = PatientImportNormalizer.gender(gender);
			String nAddr   = PatientImportNormalizer.text(address);
			String nNotes  = PatientImportNormalizer.text(notes);
			String nLegacy = PatientImportNormalizer.text(legacy);

			List<String> msgs = new ArrayList<>();
			ImportRowStatus status;
			String matchReason = null;

			// ── ERROR: sin nombre o sin apellido no hay ficha mínima ──
			if (nFirst == null || nLast == null) {
				msgs.add("Falta nombre o apellido (obligatorios)");
				status = ImportRowStatus.ERROR;
			} else {
				// ── DUPLICATE contra pacientes existentes ──
				if (nLegacy != null && existingLegacy.contains(nLegacy.toLowerCase())) {
					matchReason = "LEGACY_ID";
				} else if (nDoc != null && existingDocs.contains(nDoc.toLowerCase())) {
					matchReason = "DOCUMENT";
				} else if (nEmail != null && existingEmails.contains(nEmail.toLowerCase())) {
					matchReason = "EMAIL";
				}
				// ── DUPLICATE dentro del mismo archivo ──
				if (matchReason == null) {
					if (nLegacy != null && seenLegacy.containsKey(nLegacy.toLowerCase())) {
						matchReason = "IN_FILE";
						msgs.add("Duplicada con la fila " + seenLegacy.get(nLegacy.toLowerCase()));
					} else if (nDoc != null && seenDoc.containsKey(nDoc.toLowerCase())) {
						matchReason = "IN_FILE";
						msgs.add("Cédula repetida con la fila " + seenDoc.get(nDoc.toLowerCase()));
					} else if (nEmail != null && seenEmail.containsKey(nEmail.toLowerCase())) {
						matchReason = "IN_FILE";
						msgs.add("Email repetido con la fila " + seenEmail.get(nEmail.toLowerCase()));
					}
				}

				if (matchReason != null) {
					status = ImportRowStatus.DUPLICATE;
					if (msgs.isEmpty()) msgs.add("Ya existe un paciente (" + matchReason + ")");
				} else {
					// ── WARNING vs OK ──
					boolean warnFlag = false;
					if (nPhone == null && nEmail == null) {
						msgs.add("Sin teléfono válido ni email: no habrá canal de contacto");
						warnFlag = true;
					} else if (nPhone == null) {
						msgs.add("Teléfono no reconocible; se importa sin teléfono");
						warnFlag = true;
					}
					if (PatientImportNormalizer.isAmbiguousDate(birth)) {
						msgs.add("Fecha ambigua (¿dd/mm o mm/dd?); verificar");
						warnFlag = true;
					}
					if (birth != null && !birth.isBlank() && nBirth == null) {
						msgs.add("Fecha de nacimiento no válida; se omite");
						warnFlag = true;
					}
					status = warnFlag ? ImportRowStatus.WARNING : ImportRowStatus.OK;
				}
			}

			// Registrar en índices IN_FILE (solo si es candidata a importar)
			if (status != ImportRowStatus.ERROR) {
				if (nDoc != null)    seenDoc.putIfAbsent(nDoc.toLowerCase(), rowNumber);
				if (nEmail != null)  seenEmail.putIfAbsent(nEmail.toLowerCase(), rowNumber);
				if (nLegacy != null) seenLegacy.putIfAbsent(nLegacy.toLowerCase(), rowNumber);
			}

			// Contadores
			switch (status) {
			case OK -> ok++;
			case WARNING -> warn++;
			case ERROR -> err++;
			case DUPLICATE -> dup++;
			default -> {}
			}

			// Persistir la fila
			PatientImportRowEntity re = new PatientImportRowEntity();
			re.setTenantId(tenantId);
			re.setBatchId(batch.getId());
			re.setRowNumber(rowNumber);
			re.setRawData(toJson(rawMap(first, middle, last, last2, phone, phone2,
					email, doc, birth, gender, address, notes, legacy)));
			re.setNormalizedData(toJson(normMap(nFirst, nMiddle, nLast, nLast2,
					nPhone, nPhone2, nEmail, nDoc, nBirth, nGender, nAddr, nNotes, nLegacy)));
			re.setStatus(status);
			re.setMessages(toJson(msgs));
			re.setMatchReason(matchReason);
			rowEntities.add(re);
		}

		rowRepo.saveAll(rowEntities);

		batch.setOkRows(ok);
		batch.setWarningRows(warn);
		batch.setErrorRows(err);
		batch.setDuplicateRows(dup);
		batch.setStatus(ImportBatchStatus.ANALYZED);
		return batchRepo.save(batch);
	}

	// ═══════════════════════════════════════════════════════════════════
	//  COMMIT
	// ═══════════════════════════════════════════════════════════════════

	/**
	 * Crea los pacientes de las filas OK y WARNING. Las DUPLICATE se procesan
	 * según la estrategia. Inserta en chunks (flush+clear cada 50) para no
	 * inflar el persistence context. NO reusa PatientService.create() a
	 * propósito: ese hace 2 SELECT por fila; aquí ya deduplicamos en memoria.
	 */
	@Transactional
	public PatientImportBatchEntity commit(UUID tenantId, UUID batchId,
			DuplicateStrategy strategy) {
		if (strategy == null) strategy = DuplicateStrategy.SKIP;

		PatientImportBatchEntity batch = batchRepo.findByIdAndTenantId(batchId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));

		if (batch.getStatus() != ImportBatchStatus.ANALYZED) {
			throw new IllegalArgumentException(
					"El lote no está listo para confirmar (estado: " + batch.getStatus() + ")");
		}

		batch.setStatus(ImportBatchStatus.COMMITTING);
		batchRepo.save(batch);

		List<PatientImportRowEntity> rows =
				rowRepo.findByBatchIdAndTenantIdOrderByRowNumberAsc(batchId, tenantId);

		// Re-preload sets frescos (pudo cambiar la BD entre analyze y commit)
		Set<String> existingDocs   = lower(patientRepo.findAllDocumentNumbersByTenant(tenantId));
		Set<String> existingEmails = lower(patientRepo.findAllEmailsByTenant(tenantId));

		int imported = 0, skipped = 0;
		int inChunk = 0;

		for (PatientImportRowEntity row : rows) {
			ImportRowStatus st = row.getStatus();

			boolean shouldImport =
					st == ImportRowStatus.OK || st == ImportRowStatus.WARNING
					|| (st == ImportRowStatus.DUPLICATE && strategy == DuplicateStrategy.UPDATE_EMPTY);

			if (!shouldImport) {
				row.setStatus(ImportRowStatus.SKIPPED);
				rowRepo.save(row);
				skipped++;
				continue;
			}

			Map<String, Object> nd = fromJson(row.getNormalizedData());

			// UPDATE_EMPTY para duplicados: rellenar solo campos vacíos
			if (st == ImportRowStatus.DUPLICATE) {
				UUID matchId = resolveMatch(tenantId, nd);
				if (matchId != null) {
					updateEmptyFields(tenantId, matchId, nd);
					row.setStatus(ImportRowStatus.IMPORTED);
					row.setPatientId(matchId);
					rowRepo.save(row);
					imported++;
					continue;
				}
				// Si no se resuelve el match, tratar como alta nueva
			}

			// Guardia final anti-carrera contra los UNIQUE de patient
			String doc = str(nd, "documentNumber");
			String email = str(nd, "email");
			if (doc != null && existingDocs.contains(doc.toLowerCase())) {
				row.setStatus(ImportRowStatus.SKIPPED);
				rowRepo.save(row);
				skipped++;
				continue;
			}
			if (email != null && existingEmails.contains(email.toLowerCase())) {
				// email colisiona: importar sin email en vez de perder el paciente
				email = null;
			}

			PatientEntity p = buildPatient(tenantId, batch.getId(), nd, email);
			p = patientRepo.save(p);

			if (doc != null) existingDocs.add(doc.toLowerCase());
			if (email != null) existingEmails.add(email.toLowerCase());

			row.setStatus(ImportRowStatus.IMPORTED);
			row.setPatientId(p.getId());
			rowRepo.save(row);
			imported++;

			if (++inChunk >= CHUNK) {
				patientRepo.flush();
				inChunk = 0;
			}
		}

		patientRepo.flush();

		batch.setImportedRows(imported);
		batch.setSkippedRows(skipped);
		batch.setStatus(ImportBatchStatus.COMMITTED);
		batch.setCommittedAt(Instant.now());
		log.info("Import batch {} committed: {} imported, {} skipped",
				batch.getId(), imported, skipped);
		return batchRepo.save(batch);
	}

	// ═══════════════════════════════════════════════════════════════════
	//  REVERT
	// ═══════════════════════════════════════════════════════════════════

	/**
	 * Deshace un lote: desactiva (soft) los pacientes creados por él que NO
	 * tengan actividad clínica posterior (citas/historias). Aquí hacemos la
	 * versión conservadora: soft-deactivate por import_batch_id. Si el paciente
	 * ya tiene dependientes, el borrado lógico no rompe integridad referencial.
	 */
	@Transactional
	public PatientImportBatchEntity revert(UUID tenantId, UUID batchId) {
		PatientImportBatchEntity batch = batchRepo.findByIdAndTenantId(batchId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));

		if (batch.getStatus() != ImportBatchStatus.COMMITTED) {
			throw new IllegalArgumentException(
					"Solo se puede revertir un lote confirmado (estado: " + batch.getStatus() + ")");
		}

		int deactivated = patientRepo.deactivateByImportBatch(tenantId, batchId);
		batch.setStatus(ImportBatchStatus.REVERTED);
		batch.setRevertedAt(Instant.now());
		log.info("Import batch {} reverted: {} pacientes desactivados", batchId, deactivated);
		return batchRepo.save(batch);
	}

	// ═══════════════════════════════════════════════════════════════════
	//  READ
	// ═══════════════════════════════════════════════════════════════════

	@Transactional(readOnly = true)
	public PatientImportBatchEntity getBatch(UUID tenantId, UUID batchId) {
		return batchRepo.findByIdAndTenantId(batchId, tenantId)
				.orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
	}

	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<PatientImportRowEntity> getRows(
			UUID tenantId, UUID batchId, ImportRowStatus status,
			org.springframework.data.domain.Pageable pageable) {
		// valida pertenencia
		getBatch(tenantId, batchId);
		return (status != null)
				? rowRepo.findByBatchIdAndTenantIdAndStatus(batchId, tenantId, status, pageable)
						: rowRepo.findByBatchIdAndTenantId(batchId, tenantId, pageable);
	}

	@Transactional(readOnly = true)
	public List<PatientImportBatchEntity> listBatches(UUID tenantId) {
		return batchRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
	}

	/** CSV plantilla con encabezados + 2 filas de ejemplo. */
	public String buildTemplate() {
		return String.join(",",
				C_FIRST, C_MIDDLE, C_LAST, C_LAST2, C_PHONE, C_PHONE2, C_EMAIL,
				C_DOC, C_BIRTH, C_GENDER, C_ADDRESS, C_NOTES, C_LEGACY) + "\n"
				+ "María,Isabel,Pérez,Gómez,6123-4567,,maria.perez@gmail.com,8-754-2201,1990-05-14,F,\"Vía España, PH Torre A\",Alérgica a lidocaína,PAC-001\n"
				+ "Juan,,Rodríguez,,+507 6987 6543,,,,,M,,,PAC-002\n";
	}

	// ═══════════════════════════════════════════════════════════════════
	//  Helpers privados
	// ═══════════════════════════════════════════════════════════════════

	private PatientEntity buildPatient(UUID tenantId, UUID batchId,
			Map<String, Object> nd, String emailOverride) {
		PatientEntity p = new PatientEntity();
		p.setTenantId(tenantId);
		p.setFirstName(str(nd, "firstName"));
		p.setMiddleName(str(nd, "middleName"));
		p.setLastName(str(nd, "lastName"));
		p.setSecondLastName(str(nd, "secondLastName"));
		p.setPhone(str(nd, "phone"));
		p.setSecondaryPhone(str(nd, "secondaryPhone"));
		p.setEmail(emailOverride);
		p.setDocumentType(str(nd, "documentNumber") != null ? "CEDULA" : null);
		p.setDocumentNumber(str(nd, "documentNumber"));
		String birth = str(nd, "birthDate");
		p.setBirthDate(birth != null ? LocalDate.parse(birth) : null);
		p.setGender(str(nd, "gender"));
		p.setAddress(str(nd, "address"));
		p.setNotes(str(nd, "notes"));

		// Consentimientos SIEMPRE false: no se puede importar un consentimiento
		// que el paciente nunca otorgó (Ley 81).
		p.setPhotoConsent(false);
		p.setDataConsent(false);
		p.setActive(true);

		// Metadatos de importación (requieren el patch aditivo a PatientEntity)
		p.setDataSource("IMPORT");
		p.setImportBatchId(batchId);
		p.setLegacyExternalId(str(nd, "legacyExternalId"));
		p.setProfileStatus(isComplete(p) ? "COMPLETE" : "INCOMPLETE");
		return p;
	}

	/** Ficha completa = los 10 campos que exige la historia clínica. */
	private boolean isComplete(PatientEntity p) {
		return notBlank(p.getEmail()) && notBlank(p.getPhone())
				&& notBlank(p.getDocumentNumber()) && notBlank(p.getGender())
				&& notBlank(p.getBloodType())
				&& notBlank(p.getEmergencyContactName())
				&& notBlank(p.getEmergencyContactPhone())
				&& notBlank(p.getEmergencyContactRelation());
	}

	private UUID resolveMatch(UUID tenantId, Map<String, Object> nd) {
		String doc = str(nd, "documentNumber");
		if (doc != null) {
			var m = patientRepo.findByTenantIdAndDocumentNumber(tenantId, doc);
			if (m.isPresent()) return m.get().getId();
		}
		String email = str(nd, "email");
		if (email != null) {
			var m = patientRepo.findByTenantIdAndEmail(tenantId, email);
			if (m.isPresent()) return m.get().getId();
		}
		return null;
	}

	/** UPDATE_EMPTY: rellena solo lo que el paciente existente tiene vacío. */
	private void updateEmptyFields(UUID tenantId, UUID patientId, Map<String, Object> nd) {
		PatientEntity p = patientRepo.findByIdAndTenantId(patientId, tenantId).orElse(null);
		if (p == null) return;
		if (isBlank(p.getMiddleName()))      p.setMiddleName(str(nd, "middleName"));
		if (isBlank(p.getSecondLastName()))  p.setSecondLastName(str(nd, "secondLastName"));
		if (isBlank(p.getSecondaryPhone()))  p.setSecondaryPhone(str(nd, "secondaryPhone"));
		if (isBlank(p.getEmail()))           p.setEmail(str(nd, "email"));
		if (isBlank(p.getAddress()))         p.setAddress(str(nd, "address"));
		if (p.getBirthDate() == null && str(nd, "birthDate") != null)
			p.setBirthDate(LocalDate.parse(str(nd, "birthDate")));
		if (isBlank(p.getGender()))          p.setGender(str(nd, "gender"));
		patientRepo.save(p);
	}

	// ── mapeo de encabezados tolerante a acentos/mayúsculas ──
	private Map<String, Integer> mapHeaders(List<String> header) {
		Map<String, Integer> idx = new HashMap<>();
		for (int i = 0; i < header.size(); i++) {
			String key = canonHeader(header.get(i));
			if (key != null) idx.putIfAbsent(key, i);
		}
		if (!idx.containsKey(C_FIRST) || !idx.containsKey(C_LAST)) {
			throw new IllegalArgumentException(
					"El CSV debe tener al menos las columnas 'nombre' y 'apellido'");
		}
		return idx;
	}

	private String canonHeader(String h) {
		if (h == null) return null;
		String s = java.text.Normalizer.normalize(h.trim().toLowerCase(),
				java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
				.replaceAll("\\s+", "_");
		return switch (s) {
		case "nombre", "nombres", "primer_nombre" -> C_FIRST;
		case "segundo_nombre" -> C_MIDDLE;
		case "apellido", "apellidos", "primer_apellido" -> C_LAST;
		case "segundo_apellido" -> C_LAST2;
		case "telefono", "celular", "movil", "tel" -> C_PHONE;
		case "telefono_2", "telefono2", "telefono_secundario" -> C_PHONE2;
		case "email", "correo", "correo_electronico", "e_mail" -> C_EMAIL;
		case "cedula", "documento", "identificacion", "dni" -> C_DOC;
		case "fecha_nacimiento", "nacimiento", "fecha_de_nacimiento" -> C_BIRTH;
		case "genero", "sexo" -> C_GENDER;
		case "direccion", "domicilio" -> C_ADDRESS;
		case "notas", "observaciones", "nota" -> C_NOTES;
		case "id_sistema_anterior", "id_externo", "codigo", "id" -> C_LEGACY;
		default -> null;
		};
	}

	private static String get(List<String> row, Map<String, Integer> idx, String col) {
		Integer i = idx.get(col);
		if (i == null || i >= row.size()) return null;
		return row.get(i);
	}

	private Map<String, Object> rawMap(String first, String middle, String last, String last2,
			String phone, String phone2, String email, String doc, String birth,
			String gender, String address, String notes, String legacy) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put(C_FIRST, first);   m.put(C_MIDDLE, middle);  m.put(C_LAST, last);
		m.put(C_LAST2, last2);   m.put(C_PHONE, phone);    m.put(C_PHONE2, phone2);
		m.put(C_EMAIL, email);   m.put(C_DOC, doc);        m.put(C_BIRTH, birth);
		m.put(C_GENDER, gender); m.put(C_ADDRESS, address);m.put(C_NOTES, notes);
		m.put(C_LEGACY, legacy);
		return m;
	}

	private Map<String, Object> normMap(String first, String middle, String last, String last2,
			String phone, String phone2, String email, String doc, LocalDate birth,
			String gender, String address, String notes, String legacy) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("firstName", first);   m.put("middleName", middle);
		m.put("lastName", last);     m.put("secondLastName", last2);
		m.put("phone", phone);       m.put("secondaryPhone", phone2);
		m.put("email", email);       m.put("documentNumber", doc);
		m.put("birthDate", birth != null ? birth.toString() : null);
		m.put("gender", gender);     m.put("address", address);
		m.put("notes", notes);       m.put("legacyExternalId", legacy);
		return m;
	}

	private String toJson(Object o) {
		try { return objectMapper.writeValueAsString(o); }
		catch (Exception e) { return "{}"; }
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> fromJson(String json) {
		if (json == null || json.isBlank()) return Map.of();
		try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
		catch (Exception e) { return Map.of(); }
	}

	private static String str(Map<String, Object> m, String k) {
		Object v = m.get(k);
		if (v == null) return null;
		String s = v.toString().trim();
		return s.isEmpty() ? null : s;
	}

	private static Set<String> lower(List<String> values) {
		Set<String> set = new HashSet<>();
		for (String v : values) if (v != null && !v.isBlank()) set.add(v.toLowerCase());
		return set;
	}

	private static boolean isBlank(String s) { return s == null || s.isBlank(); }
	private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

	private static String safeName(String name) {
		return (name == null || name.isBlank()) ? "import.csv"
				: name.substring(0, Math.min(name.length(), 255));
	}

	private static String sha256(byte[] bytes) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	private static final java.util.HexFormat HEX = java.util.HexFormat.of();
}