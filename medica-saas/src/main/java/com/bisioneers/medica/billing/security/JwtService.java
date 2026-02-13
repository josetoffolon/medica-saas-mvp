package com.bisioneers.medica.billing.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generateToken(StaffUserPrincipal principal) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("medica-saas")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(principal.getUsername())
                .claim("tenantId", principal.getTenantId().toString())
                .claim("userId", principal.getUserId().toString())
                .claim("tenantAlias", principal.getTenantAlias())
                .claim("roles", principal.getAuthorities().stream().map(a -> a.getAuthority()).toList())
                .build();

        //IMPORTANTE: Header con HS256 para que Nimbus seleccione la key HMAC
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}