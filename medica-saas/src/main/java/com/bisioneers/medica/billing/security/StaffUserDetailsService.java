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
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    var u = repo.findByEmail(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    var auth = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().toUpperCase()));

    // tenantAlias: aún no existe como entidad -> lo dejamos vacío en MVP
    return new StaffUserPrincipal(
        u.getId(),
        u.getTenantId(),
        "",                  // tenantAlias (MVP)
        u.getEmail(),
        u.getPasswordHash(),
        u.isEnabled(),
        auth
    );
  }
}
