package com.bisioneers.medica.billing.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository extends JpaRepository<StaffUserEntity, UUID> {
  Optional<StaffUserEntity> findByEmail(String email);
}

