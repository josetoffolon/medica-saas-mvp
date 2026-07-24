package com.bisioneers.medica.imports.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientImportBatchRepository
extends JpaRepository<PatientImportBatchEntity, UUID> {

	Optional<PatientImportBatchEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<PatientImportBatchEntity> findByTenantIdAndFileHash(UUID tenantId, String fileHash);

	List<PatientImportBatchEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

	Page<PatientImportBatchEntity> findByTenantId(UUID tenantId, Pageable pageable);
}