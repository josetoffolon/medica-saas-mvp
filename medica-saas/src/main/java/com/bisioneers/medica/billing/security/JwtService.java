package com.bisioneers.medica.billing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
public class JwtService {

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

