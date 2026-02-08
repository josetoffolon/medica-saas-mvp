package com.bisioneers.medica.billing.security;

import com.bisioneers.medica.billing.SubscriptionEnforcementFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.bisioneers.medica.billing.SubscriptionEnforcementFilter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    SubscriptionEnforcementFilter subFilter,
                                    JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

    	http
    	  .csrf(csrf -> csrf.disable())
    	  .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    	  .authorizeHttpRequests(auth -> auth
    	      .requestMatchers("/api/public/**", "/api/auth/**", "/billing/return", "/api/billing/webhook/**",
    	                       "/swagger-ui/**", "/v3/api-docs/**").permitAll()
    	      .anyRequest().authenticated()
    	  )
    	  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
    	  .addFilterAfter(subFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http,
                                  SubscriptionEnforcementFilter subFilter) throws Exception {

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
      .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
      .addFilterAfter(subFilter, BearerTokenAuthenticationFilter.class);

    return http.build();
  }
}
