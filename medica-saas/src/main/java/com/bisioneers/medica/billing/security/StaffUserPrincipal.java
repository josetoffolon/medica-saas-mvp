package com.bisioneers.medica.billing.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.bisioneers.medica.billing.domain.TenantAware;

public class StaffUserPrincipal implements UserDetails, TenantAware {

    private final UUID userId;
    private final UUID tenantId;
    private final String tenantAlias;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public StaffUserPrincipal(
            UUID userId,
            UUID tenantId,
            String tenantAlias,
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tenantAlias = tenantAlias;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    public UUID getUserId() { return userId; }
    @Override public UUID getTenantId() { return tenantId; }
    @Override public String getTenantAlias() { return tenantAlias; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

	
}
