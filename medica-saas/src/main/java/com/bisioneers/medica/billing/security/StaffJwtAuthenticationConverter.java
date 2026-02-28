package com.bisioneers.medica.billing.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Convierte el Jwt de Spring Security en un Authentication con StaffUserPrincipal.
 *
 * Spring Security lo usa dentro de BearerTokenAuthenticationFilter.
 * El principal SIEMPRE es StaffUserPrincipal (implementa TenantAware).
 *
 * ACTUALIZACIÓN: Ahora usa StaffUserPrincipal.fromClaims() factory method
 * en vez de construir el principal manualmente.
 */
@Component
public class StaffJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        // Extraer claims del JWT
        String username = jwt.getSubject();
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenantId"));
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        String tenantAlias = jwt.getClaimAsString("tenantAlias");

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) roles = List.of();

        // Usar factory method que crea el principal desde claims
        StaffUserPrincipal principal = StaffUserPrincipal.fromClaims(
                userId, tenantId,
                tenantAlias != null ? tenantAlias : "",
                username, roles
        );

        // Retornar authentication con StaffUserPrincipal como principal
        // TenantContextFilter se encarga de setear/limpiar TenantContext ThreadLocal
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
    }
}
