package com.bisioneers.medica.consent.domain;

/**
 * Estados del ciclo de vida de una versión de plantilla de consentimiento.
 *
 *  DRAFT      → editable libremente, no puede usarse para firmar
 *  PUBLISHED  → INMUTABLE, lista para generar documentos firmables
 *  ARCHIVED   → ya no se ofrece para nuevos documentos, pero firmas previas
 *               siguen siendo válidas y trazables
 */
public enum ConsentVersionStatus {
	DRAFT,
	PUBLISHED,
	ARCHIVED
}
