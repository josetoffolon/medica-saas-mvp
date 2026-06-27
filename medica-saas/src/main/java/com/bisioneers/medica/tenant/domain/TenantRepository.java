package com.bisioneers.medica.tenant.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
	Optional<TenantEntity> findByAlias(String alias);
	Optional<TenantEntity> findByContactEmail(String email);
	boolean existsByAlias(String alias);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM TenantEntity t WHERE t.id = :id")
	Optional<TenantEntity> findByIdForUpdate(@Param("id") UUID id);
}
