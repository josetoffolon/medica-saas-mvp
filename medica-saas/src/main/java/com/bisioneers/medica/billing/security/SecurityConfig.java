package com.bisioneers.medica.billing.security;

/*
 * import com.bisioneers.medica.billing.SubscriptionEnforcementFilter; import
 * org.springframework.context.annotation.Bean; import
 * org.springframework.context.annotation.Configuration; import
 * org.springframework.security.authentication.AuthenticationManager; import
 * org.springframework.security.config.annotation.authentication.configuration.
 * AuthenticationConfiguration; import
 * org.springframework.security.config.annotation.web.builders.HttpSecurity;
 * import org.springframework.security.config.http.SessionCreationPolicy; import
 * org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import
 * org.springframework.security.crypto.password.PasswordEncoder; import
 * org.springframework.security.oauth2.server.resource.authentication.*; import
 * org.springframework.security.web.SecurityFilterChain;
 * 
 * @Configuration public class SecurityConfig {
 * 
 * @Bean SecurityFilterChain filterChain(HttpSecurity http,
 * SubscriptionEnforcementFilter subFilter) throws Exception {
 * //System.out.println(new BCryptPasswordEncoder().encode("admin1231"));
 * 
 * http .csrf(csrf -> csrf.disable()) .sessionManagement(session ->
 * session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) )
 * .authorizeHttpRequests(auth -> auth .requestMatchers( "/api/auth/**",
 * "/api/public/**", "/billing/return", "/api/billing/webhook/**",
 * "/swagger-ui/**", "/v3/api-docs/**" ).permitAll()
 * .anyRequest().authenticated() ) .oauth2ResourceServer(oauth -> oauth.jwt(jwt
 * -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) ) )
 * .addFilterAfter(subFilter,
 * org.springframework.security.oauth2.server.resource.web.authentication.
 * BearerTokenAuthenticationFilter.class);
 * 
 * return http.build(); }
 */

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
public class SecurityConfig {

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
      // JWT 100% Spring Security
      .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));

    // 1) Primero: BearerTokenAuthenticationFilter (lo agrega Spring)
    // 2) Luego: ponemos tenantContextFilter
    http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

    // 3) Y después el enforcement de suscripción (ya con tenantId disponible)
    http.addFilterAfter(subFilter, TenantContextFilter.class);

    return http.build();
  }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);

        return converter;
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