package com.bisioneers.medica.consent.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplateEntity, UUID> {

	List<ConsentTemplateEntity> findByTenantIdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID tenantId);

	List<ConsentTemplateEntity> findByTenantIdOrderByDisplayOrderAscNameAsc(UUID tenantId);

	Optional<ConsentTemplateEntity> findByTenantIdAndCode(UUID tenantId, String code);

	boolean existsByTenantIdAndCode(UUID tenantId, String code);

	/**
	 * Find with tenant validation (mejora #6: defensa en profundidad).
	 */
	Optional<ConsentTemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	/**
	 * Lock pesimista para serializar creaciones concurrentes de versiones
	 * sobre el mismo template (mejora #4: race condition prevention).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM ConsentTemplateEntity t WHERE t.id = :id AND t.tenantId = :tenantId")
	Optional<ConsentTemplateEntity> findByIdAndTenantIdForUpdate(
			@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
