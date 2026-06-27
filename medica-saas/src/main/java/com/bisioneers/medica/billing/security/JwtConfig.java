package com.bisioneers.medica.billing.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuración del encoder/decoder JWT.
 *
 * CAMBIOS vs versión anterior:
 * - JwtDecoder ahora incluye TokenBlocklistValidator
 *   (rechaza tokens revocados por logout)
 * - Issuer validation habilitada
 */
@Configuration
public class JwtConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.issuer:medica-saas}")
    private String issuer;

    private final TokenBlocklistValidator tokenBlocklistValidator;

    public JwtConfig(TokenBlocklistValidator tokenBlocklistValidator) {
        this.tokenBlocklistValidator = tokenBlocklistValidator;
    }
    
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "security.jwt.secret (env JWT_SECRET) es obligatorio y debe tener al menos " +
                "32 bytes para HS256. Genera uno con: openssl rand -base64 48");
        }
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Cadena de validación: timestamps + issuer + blocklist
        OAuth2TokenValidator<Jwt> defaultValidators =
                JwtValidators.createDefaultWithIssuer(issuer);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaultValidators,
                tokenBlocklistValidator
        ));

        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
