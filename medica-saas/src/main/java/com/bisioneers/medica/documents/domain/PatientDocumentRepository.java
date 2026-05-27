package com.bisioneers.medica.documents.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientDocumentRepository extends JpaRepository<PatientDocumentEntity, UUID> {

	List<PatientDocumentEntity> findByTenantIdAndPatientIdOrderByGeneratedAtDesc(
			UUID tenantId, UUID patientId);

	List<PatientDocumentEntity> findByTenantIdAndStatusOrderByGeneratedAtDesc(
			UUID tenantId, String status);

	Optional<PatientDocumentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
