package com.bisioneers.medica.tenant.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
    Optional<TenantEntity> findByAlias(String alias);
    Optional<TenantEntity> findByContactEmail(String email);
    boolean existsByAlias(String alias);
}
