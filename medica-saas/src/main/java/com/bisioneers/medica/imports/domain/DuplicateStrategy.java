package com.bisioneers.medica.imports.domain;

/** Qué hacer con las filas marcadas como DUPLICATE al confirmar. */
public enum DuplicateStrategy {
	SKIP,          // no tocar el paciente existente (default, seguro)
	UPDATE_EMPTY   // rellenar SOLO campos vacíos del paciente existente
}