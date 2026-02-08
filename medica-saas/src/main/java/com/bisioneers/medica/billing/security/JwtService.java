package com.bisioneers.medica.billing.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final Key signingKey;
    private final long expirationMinutes;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-minutes:60}") long expirationMinutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(StaffUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationMinutes * 60);

        Map<String, Object> claims = Map.of(
            "tenantId", principal.getTenantId().toString(),
            "tenantAlias", principal.getTenantAlias(),
            "userId", principal.getUserId().toString(),
            "roles", principal.getAuthorities().stream().map(a -> a.getAuthority()).toList()
        );

        return Jwts.builder()
            .setSubject(principal.getUsername())
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiresAt))
            .addClaims(claims)
            .signWith(signingKey)
            .compact();
    }

    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    public StaffUserPrincipal toPrincipal(Claims claims) {
        String username = claims.getSubject();
        UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
        String tenantAlias = claims.get("tenantAlias", String.class);
        UUID userId = UUID.fromString(claims.get("userId", String.class));

        List<String> roles = claims.get("roles", List.class);
        var authorities = roles.stream()
            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
            .toList();

        return new StaffUserPrincipal(
            userId,
            tenantId,
            tenantAlias,
            username,
            "",
            authorities
        );
    }
}
  private final JwtEncoder encoder;
  private final String issuer;
  private final long ttlMinutes;

  public JwtService(JwtEncoder encoder,
                    @Value("${app.jwt.issuer}") String issuer,
                    @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
    this.encoder = encoder;
    this.issuer = issuer;
    this.ttlMinutes = ttlMinutes;
  }

  public String issueStaffToken(Authentication auth) {
    var principal = (StaffUserPrincipal) auth.getPrincipal();

    Instant now = Instant.now();
    Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);

    String roles = auth.getAuthorities().stream()
        .map(a -> a.getAuthority())
        .collect(Collectors.joining(","));

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .issuedAt(now)
        .expiresAt(exp)
        .subject(principal.getUsername())
        .claim("tenantId", principal.getTenantId().toString())
        .claim("roles", roles)
        .claim("type", "STAFF")
        .build();

    return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }
}

