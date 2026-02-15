package com.bisioneers.medica.billing.audit;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

  @Value("${app.audit.system-user-id}")
  private UUID systemUserId;

  @Bean
  public AuditorAware<UUID> auditorProvider() {
    return () -> {

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();

      // Usuario autenticado
      if (auth != null && auth.isAuthenticated()) {

        Object principal = auth.getPrincipal();

        if (principal instanceof StaffUserPrincipal p) {
          return Optional.ofNullable(p.getUserId());
        }
      }
      //Fallback automático → SYSTEM USER
      return Optional.of(systemUserId);
    };
  }
}