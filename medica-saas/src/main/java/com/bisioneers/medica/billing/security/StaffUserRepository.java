package com.bisioneers.medica.billing.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository extends JpaRepository<StaffUserEntity, UUID> {
	Optional<StaffUserEntity> findByEmail(String email);
	List<StaffUserEntity> findByTenantId(UUID tenantId);
}

