package com.bisioneers.medica.billing.security;

import com.bisioneers.medica.billing.SubscriptionEnforcementFilter;
import com.bisioneers.medica.billing.tenant.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad unificada.
 *
 * CAMBIOS vs versión anterior:
 * - /api/auth/register y /api/auth/refresh ahora son públicos
 * - @EnableScheduling para limpieza automática del blocklist
 * - @EnableMethodSecurity para @PreAuthorize en controllers
 * - Imports corregidos: SubscriptionEnforcementFilter y TenantContextFilter
 *   están en paquetes diferentes (billing y billing.tenant respectivamente)
 *
 * Flujo de autenticación:
 *   Request → BearerTokenAuthFilter (Spring) →
 *   StaffJwtAuthenticationConverter → TenantContextFilter →
 *   SubscriptionEnforcementFilter → Controller
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    private final StaffJwtAuthenticationConverter staffJwtConverter;
    private final TenantContextFilter tenantContextFilter;
    private final SubscriptionEnforcementFilter subscriptionEnforcementFilter;

    public SecurityConfig(
            StaffJwtAuthenticationConverter staffJwtConverter,
            TenantContextFilter tenantContextFilter,
            SubscriptionEnforcementFilter subscriptionEnforcementFilter
    ) {
        this.staffJwtConverter = staffJwtConverter;
        this.tenantContextFilter = tenantContextFilter;
        this.subscriptionEnforcementFilter = subscriptionEnforcementFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Endpoints públicos (no requieren JWT) ──
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/api/auth/mfa/verify"
                ).permitAll()
                // Billing webhook + return (Paguelo Fácil)
                .requestMatchers(
                    "/billing/return",
                    "/api/billing/webhook/**"
                ).permitAll()
                // Swagger / OpenAPI (desarrollo)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // Actuator health (para load balancer / probes)
                .requestMatchers("/actuator/health").permitAll()
                // ── Todo lo demás requiere autenticación ──
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(staffJwtConverter))
            );

        // Filtros custom DESPUÉS del BearerTokenAuthenticationFilter de Spring
        http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(subscriptionEnforcementFilter, TenantContextFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
