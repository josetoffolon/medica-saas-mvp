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
 * Filtro que verifica la suscripción del tenant.
 *
 * Soporta grace period: si el tenant está en gracia, deja pasar
 * pero agrega headers de warning para que el frontend muestre un banner.
 *   X-Subscription-Status: GRACE_PERIOD
 *   X-Grace-Period-End: 2026-03-15T00:00:00Z
 *
 * Comportamiento:
 *   ACTIVE        → deja pasar sin headers extra
 *   GRACE_PERIOD  → deja pasar + headers de warning
 *   PAST_DUE      → bloquea con HTTP 402
 *   INACTIVE/NONE → bloquea con HTTP 402
 *
 * WHITELIST — racional de cada entrada:
 *   /api/auth               → login/refresh/MFA no dependen de la suscripción
 *   /api/public             → firma remota de pacientes (autorizada por token)
 *   /api/billing/webhook    → PF debe poder notificar pagos siempre
 *   /api/billing/checkout   → un tenant moroso DEBE poder iniciar el pago
 *   /api/subscription/me    → un tenant moroso DEBE poder consultar su estado:
 *                             lo usan el subscriptionGuard, billing-overview y
 *                             el polling de BillingReturnComponent en Angular.
 *                             Sin esto, el polling post-pago recibía 402 y el
 *                             usuario quedaba en "Procesando..." indefinidamente.
 *   /actuator, /swagger-ui, /v3/api-docs → infraestructura
 */
@Component
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(SubscriptionEnforcementFilter.class);

	private final SubscriptionService subscriptionService;

	private static final List<String> WHITELIST_PREFIXES = List.of(
			"/api/auth",
			"/api/public",
			"/api/billing/webhook",
			"/api/billing/checkout",
			"/api/subscription/me",
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

		UUID tenantId = extractTenantId(auth.getPrincipal());

		if (tenantId == null) {
			log.warn("tenantId missing in principal for request: {}", request.getRequestURI());
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"tenantId_missing_in_principal\"}");
			return;
		}

		// Una sola lectura (cacheada) en vez de 3 queries
		SubscriptionStatusSnapshot subscription = subscriptionService.getEnforcementSnapshot(tenantId);

		if (!subscription.active()) {
			log.info("Subscription blocked: tenant={}, path={}", tenantId, request.getRequestURI());
			response.setStatus(402);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write("""
					{
					  "error":"subscription_inactive",
					  "message":"Tu suscripción ha expirado. Renueva para continuar.",
					  "action":"renew",
					  "checkoutUrl":"/api/billing/checkout"
					}
					""");
			return;
		}

		// Activa — agregar warning si está en grace period
		if (subscription.inGracePeriod()) {
			response.setHeader("X-Subscription-Status", "GRACE_PERIOD");
			if (subscription.graceEnd() != null) {
				response.setHeader("X-Grace-Period-End", subscription.graceEnd().toString());
			}
			log.debug("Grace period active: tenant={}, graceEnd={}", tenantId, subscription.graceEnd());
		}

		filterChain.doFilter(request, response);
	}

	private UUID extractTenantId(Object principal) {
		if (principal instanceof TenantAware tenantAware) {
			return tenantAware.getTenantId();
		}
		log.warn("Principal is not TenantAware: {}", principal.getClass().getName());
		return null;
	}
}