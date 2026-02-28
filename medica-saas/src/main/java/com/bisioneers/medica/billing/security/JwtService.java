package com.bisioneers.medica.billing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio de generación y validación de JWT.
 *
 * CAMBIOS vs versión anterior:
 * - Expiration leída de property (antes hardcoded 3600s)
 * - Soporte para access token + refresh token con diferentes TTLs
 * - Cada token tiene un jti (JWT ID) único para poder invalidar tokens individuales
 * - Issuer leído de property
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long accessTokenMinutes;
    private final long refreshTokenMinutes;
    private final String issuer;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.expiration-minutes:60}") long accessTokenMinutes,
            @Value("${security.jwt.refresh-expiration-minutes:10080}") long refreshTokenMinutes,
            @Value("${app.jwt.issuer:medica-saas}") String issuer
    ) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenMinutes = refreshTokenMinutes;
        this.issuer = issuer;
    }

    /**
     * Genera un access token de corta duración (default 60 min).
     */
    public String generateAccessToken(StaffUserPrincipal principal) {
        return generateToken(principal, accessTokenMinutes, "access");
    }

    /**
     * Genera un refresh token de larga duración (default 7 días).
     * Contiene solo los claims mínimos necesarios para renovar.
     */
    public String generateRefreshToken(StaffUserPrincipal principal) {
        return generateToken(principal, refreshTokenMinutes, "refresh");
    }

    private String generateToken(StaffUserPrincipal principal, long ttlMinutes, String tokenType) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlMinutes * 60))
                .subject(principal.getUsername())
                .id(UUID.randomUUID().toString()) // jti: permite invalidar tokens individuales
                .claim("tenantId", principal.getTenantId().toString())
                .claim("userId", principal.getUserId().toString())
                .claim("tenantAlias", principal.getTenantAlias())
                .claim("roles", principal.getAuthorities().stream()
                        .map(a -> a.getAuthority()).toList())
                .claim("type", tokenType)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    /**
     * Extrae el claim "type" de un JWT decodificado.
     */
    public String extractTokenType(Jwt jwt) {
        String type = jwt.getClaimAsString("type");
        return type != null ? type : "access";
    }

    /**
     * Extrae el jti (JWT ID) de un JWT decodificado.
     */
    public String extractJti(Jwt jwt) {
        return jwt.getId();
    }
}
