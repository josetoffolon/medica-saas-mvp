package com.bisioneers.medica.documents.service;

import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de reemplazo de merge fields en templates HTML.
 *
 * Sintaxis: {{namespace.field}}
 *
 * Namespaces soportados:
 *   patient.*  → datos del paciente
 *   tenant.*   → datos de la clínica
 *   document.* → fecha, año, etc.
 *
 * Ejemplos:
 *   {{patient.fullName}}      → "María González Pérez"
 *   {{patient.document}}      → "8-123-456"
 *   {{patient.birthDate}}     → "15/03/1985"
 *   {{tenant.displayName}}    → "Pretelt Clinic"
 *   {{document.date}}         → "20/05/2026"
 *   {{document.year}}         → "2026"
 *
 * Campos no encontrados se reemplazan por cadena vacía (no rompen).
 */
@Component
public class MergeFieldEngine {

	private static final Pattern FIELD_PATTERN =
			Pattern.compile("\\{\\{\\s*([a-zA-Z]+)\\.([a-zA-Z_]+)\\s*\\}\\}");

	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es"));

	/**
	 * Renderiza el template reemplazando todos los merge fields.
	 */
	public String render(String template, PatientEntity patient, TenantEntity tenant) {
		if (template == null || template.isEmpty()) return "";

		Map<String, Map<String, String>> data = buildData(patient, tenant);

		StringBuilder result = new StringBuilder();
		Matcher matcher = FIELD_PATTERN.matcher(template);
		int lastEnd = 0;

		while (matcher.find()) {
			result.append(template, lastEnd, matcher.start());

			String namespace = matcher.group(1).toLowerCase();
			String field = matcher.group(2).toLowerCase();
			String value = resolveField(data, namespace, field);

			result.append(value);
			lastEnd = matcher.end();
		}
		result.append(template, lastEnd, template.length());

		return result.toString();
	}

	// ─── Construcción del mapa de datos ────────────────────────

	private Map<String, Map<String, String>> buildData(PatientEntity patient, TenantEntity tenant) {
		Map<String, Map<String, String>> data = new HashMap<>();
		data.put("patient", buildPatientData(patient));
		data.put("tenant", buildTenantData(tenant));
		data.put("document", buildDocumentData());
		return data;
	}

	private Map<String, String> buildPatientData(PatientEntity p) {
		Map<String, String> m = new HashMap<>();
		if (p == null) return m;

		m.put("fullname", safe(p.getFullName()));
		m.put("firstname", safe(p.getFirstName()));
		m.put("middlename", safe(p.getMiddleName()));
		m.put("lastname", safe(p.getLastName()));
		m.put("secondlastname", safe(p.getSecondLastName()));
		m.put("document", safe(p.getDocumentNumber()));
		m.put("documenttype", safe(p.getDocumentType()));
		m.put("documentnumber", safe(p.getDocumentNumber()));
		m.put("birthdate", formatDate(p.getBirthDate()));
		m.put("gender", "M".equals(p.getGender()) ? "Masculino"
				: "F".equals(p.getGender()) ? "Femenino" : "");
		m.put("nationality", safe(p.getNationality()));
		m.put("email", safe(p.getEmail()));
		m.put("phone", safe(p.getPhone()));
		m.put("address", safe(p.getAddress()));
		m.put("bloodtype", safe(p.getBloodType()));
		m.put("allergies", safe(p.getAllergies()));
		m.put("medicalconditions", safe(p.getMedicalConditions()));
		m.put("currentmedications", safe(p.getCurrentMedications()));
		m.put("emergencyname", safe(p.getEmergencyContactName()));
		m.put("emergencyphone", safe(p.getEmergencyContactPhone()));
		m.put("emergencyrelation", safe(p.getEmergencyContactRelation()));

		return m;
	}

	private Map<String, String> buildTenantData(TenantEntity t) {
		Map<String, String> m = new HashMap<>();
		if (t == null) return m;

		m.put("name", safe(t.getDisplayName()));
		m.put("displayname", safe(t.getDisplayName()));
		m.put("alias", safe(t.getAlias()));
		m.put("email", safe(t.getContactEmail()));
		m.put("phone", safe(t.getContactPhone()));
		m.put("address", safe(t.getAddress()));

		return m;
	}

	private Map<String, String> buildDocumentData() {
		Map<String, String> m = new HashMap<>();
		LocalDate today = LocalDate.now();
		m.put("date", today.format(DATE_FORMAT));
		m.put("year", String.valueOf(today.getYear()));
		m.put("month", String.format("%02d", today.getMonthValue()));
		m.put("day", String.format("%02d", today.getDayOfMonth()));
		return m;
	}

	// ─── Helpers ────────────────────────────────────────────────

	private String resolveField(Map<String, Map<String, String>> data,
			String namespace, String field) {
		Map<String, String> ns = data.get(namespace);
		if (ns == null) return "";
		String value = ns.get(field);
		return value != null ? value : "";
	}

	private static String safe(String s) {
		return s != null ? s : "";
	}

	private static String formatDate(LocalDate date) {
		return date != null ? date.format(DATE_FORMAT) : "";
	}
}
