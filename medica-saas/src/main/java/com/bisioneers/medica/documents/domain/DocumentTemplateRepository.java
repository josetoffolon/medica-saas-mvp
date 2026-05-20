package com.bisioneers.medica.documents.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplateEntity, UUID> {

	List<DocumentTemplateEntity> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

	List<DocumentTemplateEntity> findByTenantIdAndDocumentTypeAndActiveTrueOrderByNameAsc(
			UUID tenantId, String documentType);

	Optional<DocumentTemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	@Query("SELECT COUNT(t) > 0 FROM DocumentTemplateEntity t WHERE t.tenantId = :tenantId AND t.isSystem = true")
	boolean existsByTenantIdAndIsSystemTrue(@Param("tenantId") UUID tenantId);
}
