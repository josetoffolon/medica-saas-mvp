package com.bisioneers.medica.documents.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generador y validador de tokens para firma remota.
 *
 * Diseño:
 *  - Token = 32 bytes aleatorios (256 bits de entropía) → base64-url ~43 chars
 *  - En BD solo se guarda SHA-256 del token (no el token en claro)
 *  - El cliente recibe el token completo solo UNA vez al crear el SignatureRequest
 *  - Al validar, se hashea el token recibido y se compara con el guardado
 *
 * Razones para no usar JWT:
 *  - JWT requeriría gestionar la firma/secret, rotation, etc.
 *  - Los tokens opacos con DB lookup son más simples y revocables
 *  - SHA-256 ya da inmunidad a timing attacks vs comparación de strings
 */
@Component
public class SignatureTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	/**
	 * Genera un token random URL-safe.
	 * Ejemplo: "kHQ-c5R7nP2tF1xV...m9z" (~43 chars)
	 */
	public String generateToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Calcula SHA-256 hex del token. 64 chars.
	 */
	public String hashToken(String token) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			throw new RuntimeException("SHA-256 unavailable", e);
		}
	}
}

