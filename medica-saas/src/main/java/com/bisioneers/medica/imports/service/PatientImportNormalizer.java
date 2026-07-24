package com.bisioneers.medica.imports.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Normalización de datos crudos de CSV a valores canónicos del dominio.
 *
 * Reglas duras:
 *  - String vacío -> null SIEMPRE (evita colisión en UNIQUE(tenant_id, email))
 *  - Teléfonos a E.164 panameño (+507XXXXXXXX) porque Twilio WhatsApp lo exige
 *  - Fechas SOLO en formatos no ambiguos; dd/MM/yyyy se acepta pero se
 *    marca WARNING si el día es <= 12 (podría ser MM/dd)
 */
public final class PatientImportNormalizer {

	private PatientImportNormalizer() {}

	// ─── Encoding ─────────────────────────────────────────────────────

	/**
	 * Excel en Windows exporta CSV en Windows-1252 sin BOM. Si decodificamos
	 * como UTF-8, los acentos revientan. Heurística: intentar UTF-8 estricto;
	 * si falla, caer a Windows-1252.
	 */
	public static Charset detectCharset(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
				&& (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
			return StandardCharsets.UTF_8;
		}
		try {
			StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
			return StandardCharsets.UTF_8;
		} catch (Exception e) {
			return Charset.forName("windows-1252");
		}
	}

	// ─── Texto ────────────────────────────────────────────────────────

	/** Vacío -> null. Colapsa espacios internos. Trim. */
	public static String text(String v) {
		if (v == null) return null;
		String t = v.trim().replaceAll("\\s+", " ");
		return t.isEmpty() ? null : t;
	}

	/** Nombre propio: "JOSE  toffolon" -> "Jose Toffolon" */
	public static String properName(String v) {
		String t = text(v);
		if (t == null) return null;
		StringBuilder sb = new StringBuilder();
		for (String w : t.toLowerCase().split(" ")) {
			if (w.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	// ─── Teléfono Panamá -> E.164 ─────────────────────────────────────

	/**
	 * Acepta: 6123-4567, 61234567, +507 6123 4567, 507-6123-4567, 269-1234
	 * Devuelve: +50761234567  /  +5072691234
	 * Devuelve null si no es reconocible (la fila queda con WARNING, no ERROR:
	 * un paciente sin teléfono válido igual se puede migrar).
	 */
	public static String phonePa(String v) {
		String t = text(v);
		if (t == null) return null;
		String d = t.replaceAll("[^0-9]", "");
		if (d.startsWith("00507")) d = d.substring(5);
		else if (d.startsWith("507") && d.length() > 8) d = d.substring(3);
		if (d.length() == 8 && d.charAt(0) == '6') return "+507" + d;   // móvil
		if (d.length() == 7) return "+507" + d;                          // fijo
		if (d.length() == 8) return "+507" + d;                          // móvil no-6
		return null;
	}

	// ─── Email ────────────────────────────────────────────────────────

	private static final java.util.regex.Pattern EMAIL =
			java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$");

	/** Inválido -> null (no bloquea la fila; el email es opcional al migrar). */
	public static String email(String v) {
		String t = text(v);
		if (t == null) return null;
		t = t.toLowerCase();
		return EMAIL.matcher(t).matches() ? t : null;
	}

	// ─── Cédula panameña ──────────────────────────────────────────────

	/** Normaliza separadores: "8 754 2201" / "8-754-2201" -> "8-754-2201" */
	public static String documentPa(String v) {
		String t = text(v);
		if (t == null) return null;
		return t.toUpperCase().replaceAll("[\\s._]+", "-").replaceAll("-{2,}", "-");
	}

	// ─── Fecha ────────────────────────────────────────────────────────

	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
			DateTimeFormatter.ofPattern("yyyy-MM-dd"),
			DateTimeFormatter.ofPattern("dd/MM/yyyy"),
			DateTimeFormatter.ofPattern("dd-MM-yyyy"),
			DateTimeFormatter.ofPattern("d/M/yyyy"));

	/** null si no parsea. Nunca lanza: una fecha mala no debe abortar la fila. */
	public static LocalDate date(String v) {
		String t = text(v);
		if (t == null) return null;
		for (DateTimeFormatter f : DATE_FORMATS) {
			try {
				LocalDate d = LocalDate.parse(t, f);
				if (d.isAfter(LocalDate.now()) || d.getYear() < 1900) return null;
				return d;
			} catch (Exception ignored) {}
		}
		return null;
	}

	/** true si la fecha es ambigua entre dd/MM y MM/dd -> generar WARNING. */
	public static boolean isAmbiguousDate(String v) {
		String t = text(v);
		if (t == null || !t.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}")) return false;
		String[] p = t.split("[/-]");
		return Integer.parseInt(p[0]) <= 12 && Integer.parseInt(p[1]) <= 12;
	}

	// ─── Género ───────────────────────────────────────────────────────

	/** M/F/masculino/femenino/hombre/mujer/1/2 -> "M"|"F"|null */
	public static String gender(String v) {
		String t = text(v);
		if (t == null) return null;
		String s = t.toUpperCase();
		if (s.startsWith("M") || s.startsWith("H") || s.equals("1")) return "M";
		if (s.startsWith("F") || s.startsWith("MUJ") || s.equals("2")) return "F";
		return null;
	}

	// ─── Clave de deduplicación ───────────────────────────────────────

	/** Fallback cuando no hay cédula ni email: nombre normalizado + teléfono. */
	public static String fuzzyKey(String firstName, String lastName, String phoneE164) {
		String n = (stripAccents(firstName) + "|" + stripAccents(lastName)).toLowerCase();
		return n + "|" + (phoneE164 == null ? "" : phoneE164);
	}

	private static String stripAccents(String s) {
		if (s == null) return "";
		return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
	}
}