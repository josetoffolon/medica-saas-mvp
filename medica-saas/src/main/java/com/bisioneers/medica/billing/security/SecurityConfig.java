package com.bisioneers.medica.billing.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.bisioneers.medica.billing.SubscriptionEnforcementFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    SubscriptionEnforcementFilter subFilter) throws Exception {

    	http
    	  .csrf(csrf -> csrf.disable())
    	  .httpBasic(basic -> {})
    	  .authorizeHttpRequests(auth -> auth
    	      .requestMatchers("/api/public/**", "/api/auth/**", "/billing/return", "/api/billing/webhook/**",
    	                       "/swagger-ui/**", "/v3/api-docs/**").permitAll()
    	      .anyRequest().authenticated()
    	  )
    	  .addFilterAfter(subFilter, BasicAuthenticationFilter.class);

        return http.build();
    }
}

