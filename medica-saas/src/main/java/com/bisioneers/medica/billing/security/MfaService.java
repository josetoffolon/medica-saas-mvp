package com.bisioneers.medica.billing.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de negocio para gestión de MFA por usuario.
 *
 * Flujo de activación:
 *   1. setup() → genera secreto, lo guarda en BD pero MFA aún no activo
 *   2. Usuario escanea QR en su app authenticator
 *   3. enable() → verifica el primer código y activa MFA
 *
 * Flujo de desactivación:
 *   disable() → requiere password actual + código TOTP válido
 */
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final StaffUserRepository staffRepository;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final String issuerName;

    public MfaService(StaffUserRepository staffRepository,
                      TotpService totpService,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.name:Medica SaaS}") String issuerName) {
        this.staffRepository = staffRepository;
        this.totpService = totpService;
        this.passwordEncoder = passwordEncoder;
        this.issuerName = issuerName;
    }

    /**
     * Genera un nuevo secreto MFA para el usuario y lo guarda en BD
     * (pero sin activar todavía). Retorna el secreto + URI para el QR.
     *
     * Si el usuario ya tiene MFA activo, falla.
     * Si tiene un setup pendiente, lo regenera (sobrescribe).
     */
    @Transactional
    public MfaSetupResult setup(UUID userId) {
        StaffUserEntity user = staffRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (user.isMfaEnabled()) {
            throw new IllegalStateException("MFA ya está activo. Desactívalo primero.");
        }

        String secret = totpService.generateSecret();
        String otpAuthUri = totpService.buildOtpAuthUri(secret, user.getEmail(), issuerName);

        user.setMfaSecret(secret);
        user.setMfaEnabled(false); // aún no activo, solo preparado
        staffRepository.save(user);

        log.info("MFA setup initiated for user {}", user.getEmail());
        return new MfaSetupResult(secret, otpAuthUri);
    }

    /**
     * Activa MFA para el usuario verificando el primer código.
     * Confirma que el usuario configuró correctamente su app authenticator.
     */
    @Transactional
    public void enable(UUID userId, String code) {
        StaffUserEntity user = staffRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (user.isMfaEnabled()) {
            throw new IllegalStateException("MFA ya está activo");
        }
        if (user.getMfaSecret() == null) {
            throw new IllegalStateException("Primero ejecuta setup() para generar el secreto");
        }
        if (!totpService.verifyCode(user.getMfaSecret(), code)) {
            throw new IllegalArgumentException("Código inválido. Verifica que escaneaste el QR correctamente.");
        }

        user.setMfaEnabled(true);
        user.setMfaActivatedAt(Instant.now());
        staffRepository.save(user);

        log.info("MFA enabled for user {}", user.getEmail());
    }

    /**
     * Desactiva MFA. Requiere password actual + código TOTP para confirmar identidad.
     */
    @Transactional
    public void disable(UUID userId, String currentPassword, String code) {
        StaffUserEntity user = staffRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!user.isMfaEnabled()) {
            throw new IllegalStateException("MFA no está activo");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Contraseña incorrecta");
        }
        if (!totpService.verifyCode(user.getMfaSecret(), code)) {
            throw new IllegalArgumentException("Código MFA inválido");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaActivatedAt(null);
        staffRepository.save(user);

        log.info("MFA disabled for user {}", user.getEmail());
    }

    /**
     * Verifica un código TOTP contra el secreto del usuario.
     * Usado durante el flujo de login para validar el segundo factor.
     */
    public boolean verifyCode(UUID userId, String code) {
        StaffUserEntity user = staffRepository.findById(userId).orElse(null);
        if (user == null || !user.isMfaEnabled() || user.getMfaSecret() == null) {
            return false;
        }
        return totpService.verifyCode(user.getMfaSecret(), code);
    }

    /** Estado MFA del usuario para mostrar en settings */
    public MfaStatus getStatus(UUID userId) {
        StaffUserEntity user = staffRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return new MfaStatus(user.isMfaEnabled(), user.getMfaActivatedAt());
    }

    // ─── Result DTOs ──────────────────────────────────

    public record MfaSetupResult(String secret, String otpAuthUri) {}

    public record MfaStatus(boolean enabled, Instant activatedAt) {}
}
