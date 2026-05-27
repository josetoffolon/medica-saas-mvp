package com.bisioneers.medica.consent.domain;

import java.util.UUID;

/**
 * Proyección plana usada en findStatsByTenantId() para evitar N+1
 * al listar plantillas con su versión actual + total de versiones.
 */
public record TemplateVersionStats(
		UUID templateId,
		Integer currentVersionNumber,  // null si no hay versión publicada
		Long totalVersions
		) {}
