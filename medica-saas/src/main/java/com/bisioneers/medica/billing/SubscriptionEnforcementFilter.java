package com.bisioneers.medica.billing;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bisioneers.medica.billing.domain.TenantAware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro que bloquea requests si la suscripción del tenant no está activa.
 * 
 * CAMBIOS:
 * - extractTenantId ahora usa TenantAware (interfaz que StaffUserPrincipal implementa)
 *   en vez de castear a Jwt directamente. Esto es consistente con el nuevo
 *   StaffJwtAuthenticationConverter que siempre produce StaffUserPrincipal.
 * - Se reemplazaron System.out.println por SLF4J Logger.
 */
@Component
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEnforcementFilter.class);

    private final SubscriptionService subscriptionService;

    private static final List<String> WHITELIST_PREFIXES = List.of(
            "/api/auth",
            "/api/public",
            "/billing/return",
            "/api/billing/webhook",
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    );

    public SubscriptionEnforcementFilter(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return WHITELIST_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        // Sin autenticación → dejar pasar (Spring Security manejará el 401)
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Solo verificar suscripción para roles de staff
        boolean isStaff = auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) ||
                "ROLE_MEDICO".equals(a.getAuthority()) ||
                "ROLE_RECEPCION".equals(a.getAuthority()) ||
                "ROLE_ASISTENTE".equals(a.getAuthority())
        );

        if (!isStaff) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer tenantId del principal (ahora siempre es TenantAware)
        UUID tenantId = extractTenantId(auth.getPrincipal());

        if (tenantId == null) {
            log.warn("tenantId missing in principal for request: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"tenantId_missing_in_principal\"}");
            return;
        }

        boolean active = subscriptionService.isActive(tenantId);
        log.debug("Subscription check for tenant {}: active={}", tenantId, active);

        if (!active) {
            response.setStatus(402);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String body = """
            {
              "error":"subscription_inactive",
              "message":"Subscription is not active",
              "action":"renew",
              "checkoutUrl":"/api/billing/checkout"
            }
            """;

            response.getWriter().write(body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrae tenantId del principal.
     * 
     * ANTES: casteaba a org.springframework.security.oauth2.jwt.Jwt
     *        (fallaba cuando el converter custom producía StaffUserPrincipal)
     * 
     * AHORA: castea a TenantAware (interfaz que StaffUserPrincipal implementa)
     *        Esto funciona siempre porque StaffJwtAuthenticationConverter 
     *        garantiza que el principal es StaffUserPrincipal.
     */
    private UUID extractTenantId(Object principal) {
        if (principal instanceof TenantAware tenantAware) {
            return tenantAware.getTenantId();
        }
        log.warn("Principal is not TenantAware: {}", principal.getClass().getName());
        return null;
    }
}
