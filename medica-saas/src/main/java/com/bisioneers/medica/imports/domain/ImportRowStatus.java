package com.bisioneers.medica.imports.domain;

/** Estado de una fila individual del CSV. */
public enum ImportRowStatus {
	OK,         // lista para importar
	WARNING,    // se importa pero con avisos (teléfono no parseable, fecha ambigua...)
	ERROR,      // no se puede importar (falta nombre/apellido)
	DUPLICATE,  // coincide con paciente existente o con otra fila del mismo archivo
	IMPORTED,   // ya creada como paciente (post-commit)
	SKIPPED     // omitida en commit (duplicado con estrategia SKIP, o ERROR)
}