package com.bisioneers.medica.billing.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffUserDetailsService implements UserDetailsService {

  private final StaffUserRepository repo;

  public StaffUserDetailsService(StaffUserRepository repo) {
    this.repo = repo;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    StaffUserEntity u = repo.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    var authorities = List.of(
        new SimpleGrantedAuthority("ROLE_" + u.getRole().toUpperCase())
    );

    return new StaffUserPrincipal(
        u.getId(),
        u.getTenantId(),
        "",                 // tenantAlias (si aún no existe en DB, déjalo "")
        u.getEmail(),
        u.getPasswordHash(),
        u.isEnabled(),
        authorities
    );
  }
}
