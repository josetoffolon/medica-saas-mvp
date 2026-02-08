package com.bisioneers.medica.billing.security;

import com.bisioneers.medica.billing.domain.TenantAware;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public class StaffUserPrincipal implements UserDetails, TenantAware {

  private final UUID userId;
  private final UUID tenantId;
  private final String email;
  private final String passwordHash;
  private final boolean enabled;
  private final Collection<? extends GrantedAuthority> authorities;

  public StaffUserPrincipal(UUID userId, UUID tenantId, String email, String passwordHash,
                            boolean enabled, Collection<? extends GrantedAuthority> authorities) {
    this.userId = userId;
    this.tenantId = tenantId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.enabled = enabled;
    this.authorities = authorities;
  }

  public UUID getUserId() { return userId; }

  @Override public UUID getTenantId() { return tenantId; }
  @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
  @Override public String getPassword() { return passwordHash; }
  @Override public String getUsername() { return email; }
  @Override public boolean isAccountNonExpired() { return true; }
  @Override public boolean isAccountNonLocked() { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled() { return enabled; }

  @Override
  public String getTenantAlias() {
	// TODO Auto-generated method stub
	return null;
  }
}
