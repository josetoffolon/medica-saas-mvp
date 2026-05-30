package com.bisioneers.medica.documents.domain;

/**
 * Método de generación del flujo de firma.
 *
 *  IN_PERSON  → firmado en el mismo dispositivo del staff (token NO se usa)
 *  REMOTE     → link enviado al paciente, firma desde su propio dispositivo
 */
public enum SignatureMethod {
	IN_PERSON,
	REMOTE
}
