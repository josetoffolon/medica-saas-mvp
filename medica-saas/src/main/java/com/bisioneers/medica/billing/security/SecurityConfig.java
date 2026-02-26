package com.bisioneers.medica.billing.security;

import com.bisioneers.medica.billing.SubscriptionEnforcementFilter;
import com.bisioneers.medica.billing.tenant.TenantContextFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * Configuración de seguridad unificada.
 * 
 * CAMBIOS:
 * - Se eliminó el JwtAuthenticationConverter genérico (que producía un Jwt como principal)
 * - Se usa StaffJwtAuthenticationConverter que produce StaffUserPrincipal como principal
 * - Se eliminó la necesidad de JwtAuthenticationFilter custom (BORRAR ese archivo)
 * - La cadena de filtros queda: BearerTokenAuth → TenantContext → SubscriptionEnforcement
 */
@Configuration
public class SecurityConfig {

    private final StaffJwtAuthenticationConverter staffJwtConverter;

    public SecurityConfig(StaffJwtAuthenticationConverter staffJwtConverter) {
        this.staffJwtConverter = staffJwtConverter;
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            TenantContextFilter tenantContextFilter,
            SubscriptionEnforcementFilter subFilter
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/public/**",
                    "/api/auth/**",
                    "/billing/return",
                    "/api/billing/webhook/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // JWT: Spring Security maneja el Bearer token y usa nuestro converter
            // para producir StaffUserPrincipal (TenantAware) como principal
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(staffJwtConverter))
            );

        // Filtros adicionales DESPUÉS del BearerTokenAuthenticationFilter de Spring:
        // 1) TenantContextFilter: extrae tenantId del principal y lo pone en ThreadLocal
        http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

        // 2) SubscriptionEnforcementFilter: verifica suscripción activa
        http.addFilterAfter(subFilter, TenantContextFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
