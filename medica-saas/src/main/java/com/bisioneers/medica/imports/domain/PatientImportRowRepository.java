package com.bisioneers.medica.imports.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientImportRowRepository
extends JpaRepository<PatientImportRowEntity, UUID> {

	/** Todas las filas de un lote (para commit), ordenadas. */
	List<PatientImportRowEntity> findByBatchIdAndTenantIdOrderByRowNumberAsc(
			UUID batchId, UUID tenantId);

	/** Filas de un lote filtradas por estado, paginadas (para preview). */
	Page<PatientImportRowEntity> findByBatchIdAndTenantIdAndStatus(
			UUID batchId, UUID tenantId, ImportRowStatus status, Pageable pageable);

	Page<PatientImportRowEntity> findByBatchIdAndTenantId(
			UUID batchId, UUID tenantId, Pageable pageable);

	long countByBatchIdAndTenantIdAndStatus(
			UUID batchId, UUID tenantId, ImportRowStatus status);
}