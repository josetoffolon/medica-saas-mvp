package com.bisioneers.medica.billing.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Convierte el Jwt de Spring Security en un Authentication con StaffUserPrincipal.
 * 
 * PROBLEMA RESUELTO:
 * Antes existían 2 mecanismos compitiendo:
 *   1) JwtAuthenticationFilter (custom) → creaba UsernamePasswordAuthenticationToken con StaffUserPrincipal
 *   2) oauth2ResourceServer().jwt() → creaba JwtAuthenticationToken con Jwt como principal
 * 
 * Esto causaba que TenantContextFilter (que espera TenantAware) y 
 * SubscriptionEnforcementFilter (que casteaba a Jwt) fallaran intermitentemente.
 * 
 * SOLUCIÓN: Un solo converter que Spring usa internamente en BearerTokenAuthenticationFilter.
 * El principal SIEMPRE es StaffUserPrincipal (implementa TenantAware).
 */
@Component
public class StaffJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        // 1) Extraer claims del JWT
        String username = jwt.getSubject();
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenantId"));
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        String tenantAlias = jwt.getClaimAsString("tenantAlias");

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) roles = List.of();

        var authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        // 2) Construir StaffUserPrincipal (implementa TenantAware + UserDetails)
        StaffUserPrincipal principal = new StaffUserPrincipal(
                userId,
                tenantId,
                tenantAlias != null ? tenantAlias : "",
                username,
                "",       // password no necesario post-auth
                true,
                authorities
        );

        // 3) NO establecer TenantContext aquí.
        //    El TenantContextFilter se encarga de setear y limpiar el ThreadLocal.
        //    Si lo seteamos aquí, podría quedarse sin limpiar en rutas whitelisted.

        // 4) Retornar authentication con StaffUserPrincipal como principal
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, jwt, authorities);

        return authentication;
    }
}
