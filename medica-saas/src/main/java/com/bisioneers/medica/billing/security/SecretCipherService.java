package com.bisioneers.medica.billing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra/descifra secretos sensibles (ej: secreto TOTP) con AES-GCM.
 *
 * Formato del valor almacenado (Base64): [ IV(12 bytes) | ciphertext+tag ]
 * GCM aporta confidencialidad + integridad (el descifrado falla si el dato
 * fue manipulado).
 *
 * La llave se inyecta como Base64 (16/24/32 bytes). Generar con:
 *   openssl rand -base64 32
 */
@Component
public class SecretCipherService {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;        // 96 bits, recomendado para GCM
	private static final int TAG_LENGTH_BITS = 128; // tag de autenticación

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public SecretCipherService(@Value("${security.mfa.encryption-key}") String base64Key) {
		byte[] keyBytes = Base64.getDecoder().decode(base64Key);
		if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
			throw new IllegalStateException(
					"security.mfa.encryption-key debe ser AES-128/192/256 (16, 24 o 32 bytes en Base64). " +
					"Genera una con: openssl rand -base64 32");
		}
		this.key = new SecretKeySpec(keyBytes, ALGORITHM);
	}

	public String encrypt(String plaintext) {
		if (plaintext == null) return null;
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			byte[] combined = new byte[iv.length + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception e) {
			throw new IllegalStateException("Error cifrando secreto", e);
		}
	}

	public String decrypt(String encrypted) {
		if (encrypted == null) return null;
		try {
			byte[] combined = Base64.getDecoder().decode(encrypted);
			byte[] iv = new byte[IV_LENGTH];
			byte[] ciphertext = new byte[combined.length - IV_LENGTH];
			System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
			System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException(
					"Error descifrando secreto MFA (¿llave incorrecta o dato corrupto?)", e);
		}
	}
}
