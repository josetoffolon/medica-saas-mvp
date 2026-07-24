package com.bisioneers.medica.imports.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lector CSV mínimo y correcto (RFC-4180): soporta comillas dobles,
 * comas y saltos de línea dentro de campos entrecomillados, y "" como
 * comilla escapada. Autodetecta separador , o ; (Excel en locale ES usa ;).
 *
 * No es un parser de streaming: carga todo en memoria. Suficiente para
 * los <2.000 registros del MVP; por encima, migrar a commons-csv + cola.
 */
public final class SimpleCsvReader {

	private SimpleCsvReader() {}

	public static List<List<String>> parse(String content) {
		char sep = detectSeparator(content);
		List<List<String>> rows = new ArrayList<>();
		List<String> current = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;

		int i = 0;
		int n = content.length();
		while (i < n) {
			char c = content.charAt(i);

			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < n && content.charAt(i + 1) == '"') {
						field.append('"');
						i += 2;
					} else {
						inQuotes = false;
						i++;
					}
				} else {
					field.append(c);
					i++;
				}
			} else {
				if (c == '"') {
					inQuotes = true;
					i++;
				} else if (c == sep) {
					current.add(field.toString());
					field.setLength(0);
					i++;
				} else if (c == '\r') {
					i++; // se maneja con el \n siguiente
				} else if (c == '\n') {
					current.add(field.toString());
					field.setLength(0);
					rows.add(current);
					current = new ArrayList<>();
					i++;
				} else {
					field.append(c);
					i++;
				}
			}
		}
		// último campo/fila si el archivo no termina en salto de línea
		if (field.length() > 0 || !current.isEmpty()) {
			current.add(field.toString());
			rows.add(current);
		}
		// descartar filas totalmente vacías
		rows.removeIf(r -> r.stream().allMatch(s -> s == null || s.isBlank()));
		return rows;
	}

	/** Cuenta , vs ; en la primera línea y elige el mayoritario. */
	private static char detectSeparator(String content) {
		int nl = content.indexOf('\n');
		String firstLine = nl >= 0 ? content.substring(0, nl) : content;
		long commas = firstLine.chars().filter(ch -> ch == ',').count();
		long semis = firstLine.chars().filter(ch -> ch == ';').count();
		return semis > commas ? ';' : ',';
	}
}