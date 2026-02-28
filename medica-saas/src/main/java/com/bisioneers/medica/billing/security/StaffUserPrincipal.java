package com.bisioneers.medica.billing.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.bisioneers.medica.billing.domain.TenantAware;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Principal unificado que implementa UserDetails + TenantAware.
 *
 * Usado en TODO el flujo de seguridad:
 * - StaffUserDetailsService lo crea para login (AuthenticationManager)
 * - StaffJwtAuthenticationConverter lo crea desde claims JWT (requests autenticados)
 * - AuthController lo crea vía fromEntity() para generar tokens post-registro/refresh
 *
 * CORRECCIONES vs versión anterior:
 * - fromStaff(Staff) → fromEntity(StaffUserEntity, String tenantAlias)
 *   porque StaffUserEntity no tiene relación @ManyToOne con Tenant,
 *   solo tiene tenantId (UUID). El alias se pasa explícitamente.
 * - Usa isEnabled() en vez de isActive() (campo real de StaffUserEntity)
 * - Eliminado import de com.bisioneers.medica.billing.model.Staff (no existe)
 */
public class StaffUserPrincipal implements UserDetails, TenantAware {

    private final UUID userId;
    private final UUID tenantId;
    private final String tenantAlias;
    private final String email;
    private final String password;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public StaffUserPrincipal(UUID userId, UUID tenantId, String tenantAlias,
                              String email, String password, boolean active,
                              Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tenantAlias = tenantAlias;
        this.email = email;
        this.password = password;
        this.active = active;
        this.authorities = authorities;
    }

    /**
     * Crea un StaffUserPrincipal desde una entidad StaffUserEntity.
     * Usado en refresh token y registro (donde no hay JWT ni Authentication).
     *
     * NOTA: StaffUserEntity solo tiene tenantId (UUID), no una relación
     * con TenantEntity, por eso el alias se pasa como parámetro separado.
     * El caller debe cargar el TenantEntity previamente para obtener el alias.
     */
    public static StaffUserPrincipal fromEntity(StaffUserEntity user, String tenantAlias) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole())
        );
        return new StaffUserPrincipal(
                user.getId(),
                user.getTenantId(),
                tenantAlias != null ? tenantAlias : "",
                user.getEmail(),
                user.getPasswordHash(),
                user.isEnabled(),
                authorities
        );
    }

    /**
     * Crea un StaffUserPrincipal desde claims JWT.
     * Usado por StaffJwtAuthenticationConverter.
     */
    public static StaffUserPrincipal fromClaims(UUID userId, UUID tenantId, String tenantAlias,
                                                 String email, List<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new StaffUserPrincipal(
                userId, tenantId, tenantAlias,
                email, "[PROTECTED]", true, authorities
        );
    }

    // ─── TenantAware ──────────────────────────────────────────────────

    @Override
    public UUID getTenantId() { return tenantId; }

    public String getTenantAlias() { return tenantAlias; }

    public UUID getUserId() { return userId; }

    // ─── UserDetails ──────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return active; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return active; }

    @Override
    public boolean isEnabled() { return active; }
}
