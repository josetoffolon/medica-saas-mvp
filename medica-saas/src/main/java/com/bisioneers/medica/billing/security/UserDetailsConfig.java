package com.bisioneers.medica.billing.security;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserDetailsConfig {

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return username -> {

            UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

            var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEDICO")
            );

            return new StaffUserPrincipal(
                userId,
                tenantId,
                "demo-tenant",
                "admin@demo.com",
                passwordEncoder.encode("admin123"), // SOLO DEV
                authorities
            );
        };
    }
  @Bean
  public UserDetailsService userDetailsService() {
    return username -> {

      UUID userId   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
      UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

      var authorities = List.of(new SimpleGrantedAuthority("ROLE_MEDICO"));

      return new StaffUserPrincipal(
          userId,
          tenantId,
          "admin@demo.com",
          "{noop}admin123", // SOLO DEV
          true,
          authorities
      );
    };
  }
}
