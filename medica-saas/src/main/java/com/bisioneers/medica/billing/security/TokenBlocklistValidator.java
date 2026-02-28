package com.bisioneers.medica.billing.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Validador custom que Spring Security ejecuta DESPUÉS de decodificar el JWT.
 * Rechaza tokens cuyo jti esté en el blocklist (logout).
 *
 * Se registra en JwtConfig vía:
 *   jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
 *       JwtValidators.createDefaultWithIssuer(issuer),
 *       tokenBlocklistValidator
 *   ));
 */
@Component
public class TokenBlocklistValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED_ERROR =
            new OAuth2Error("token_revoked", "Token has been revoked", null);

    private final TokenBlocklistService blocklistService;

    public TokenBlocklistValidator(TokenBlocklistService blocklistService) {
        this.blocklistService = blocklistService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        if (jti != null && blocklistService.isRevoked(jti)) {
            return OAuth2TokenValidatorResult.failure(REVOKED_ERROR);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
