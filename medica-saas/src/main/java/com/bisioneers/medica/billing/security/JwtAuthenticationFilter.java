package com.bisioneers.medica.billing.security;

import com.bisioneers.medica.billing.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {

            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {

                String token = authHeader.substring(7);

                Jwt jwt = jwtDecoder.decode(token);

                String username = jwt.getSubject();
                UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenantId"));
                UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
                String tenantAlias = jwt.getClaimAsString("tenantAlias");

                List<String> roles = jwt.getClaimAsStringList("roles");

                var authorities = roles.stream()
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList();

                StaffUserPrincipal principal = new StaffUserPrincipal(
                        userId,
                        tenantId,
                        tenantAlias,
                        username,
                        "",
                        true,
                        authorities
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                authorities
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Set tenant en ThreadLocal
                TenantContext.setTenantId(tenantId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // MUY IMPORTANTE: limpiar siempre
            TenantContext.clear();
        }
    }
}