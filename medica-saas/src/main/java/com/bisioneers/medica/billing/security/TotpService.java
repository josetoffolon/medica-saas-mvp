package com.bisioneers.medica.billing.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Generación y verificación de códigos TOTP según RFC 6238.
 *
 * Compatible con Google Authenticator, Authy, 1Password, Microsoft Authenticator.
 *
 * Algoritmo: HMAC-SHA1, ventana de 30 segundos, código de 6 dígitos.
 * Acepta el código actual + 1 ventana antes/después (tolerancia clock drift).
 */
@Service
public class TotpService {

    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int WINDOW = 1; // Acepta -1, 0, +1 ventanas
    private static final int SECRET_BYTES = 20; // 160 bits, recomendado

    // Base32 alphabet (RFC 4648) - usado por Google Authenticator
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom random = new SecureRandom();

    /**
     * Genera un nuevo secreto MFA aleatorio en Base32.
     * Este secreto se debe guardar cifrado en la base de datos.
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * Construye la URI otpauth:// para generar el QR code.
     * Esta URI es la que el usuario escanea con Google Authenticator.
     *
     * Ejemplo: otpauth://totp/Medica%20SaaS:user@example.com?secret=ABC123&issuer=Medica%20SaaS
     */
    public String buildOtpAuthUri(String secret, String userEmail, String issuer) {
        String encodedIssuer = urlEncode(issuer);
        String encodedEmail = urlEncode(userEmail);
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                encodedIssuer, encodedEmail, secret, encodedIssuer
        );
    }

    /**
     * Verifica si el código provisto es válido para el secreto dado.
     * Acepta una ventana de tolerancia (±30s) por clock drift.
     */
    public boolean verifyCode(String secret, String code) {
        if (code == null || code.length() != CODE_DIGITS) return false;

        try {
            int userCode = Integer.parseInt(code);
            long currentTimeStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            byte[] secretBytes = base32Decode(secret);

            for (int i = -WINDOW; i <= WINDOW; i++) {
                int generatedCode = generateCode(secretBytes, currentTimeStep + i);
                if (constantTimeEquals(generatedCode, userCode)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── TOTP Algorithm ───────────────────────────────

    private int generateCode(byte[] secret, long timeStep) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation (RFC 4226)
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24)
                       | ((hash[offset + 1] & 0xFF) << 16)
                       | ((hash[offset + 2] & 0xFF) << 8)
                       | (hash[offset + 3] & 0xFF);

            return binary % 1_000_000;
        } catch (Exception e) {
            throw new RuntimeException("TOTP generation failed", e);
        }
    }

    /** Constant-time comparison to prevent timing attacks */
    private boolean constantTimeEquals(int a, int b) {
        return ((a ^ b) | -(a ^ b)) >>> 31 == 0;
    }

    // ─── Base32 (RFC 4648) ────────────────────────────

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    private byte[] base32Decode(String encoded) {
        encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] result = new byte[encoded.length() * 5 / 8];
        int bits = 0, value = 0, index = 0;
        for (char c : encoded.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) continue;
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                result[index++] = (byte) ((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return result;
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}

