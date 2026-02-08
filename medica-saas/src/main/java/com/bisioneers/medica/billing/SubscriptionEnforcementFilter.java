package com.bisioneers.medica.billing;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bisioneers.medica.billing.domain.TenantAware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

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

        System.out.println("SubscriptionEnforcementFilter -> " + request.getRequestURI());

        var auth = SecurityContextHolder.getContext().getAuthentication();

        // IMPORTANTÍSIMO: auth puede ser null si no hay login
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Logs (ya es seguro)
        System.out.println("AUTH principal class: " + auth.getPrincipal().getClass());
        System.out.println("AUTH authorities: " + auth.getAuthorities());

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

        UUID tenantId = extractTenantId(auth.getPrincipal());
        System.out.println("tenantId: " + tenantId);

        if (tenantId == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"tenantId_missing_in_principal\"}");
            return;
        }

        boolean active = subscriptionService.isActive(tenantId);
        System.out.println("subscription active? " + active);

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


    private UUID extractTenantId(Object principal) {
    	  if (principal instanceof com.bisioneers.medica.billing.domain.TenantAware ta) {
    	    return ta.getTenantId();
    	  }
    	  if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
    	    String tenantId = jwt.getClaimAsString("tenantId");
    	    if (tenantId != null) return UUID.fromString(tenantId);
    	  }
    	  return null;
    	}

}

