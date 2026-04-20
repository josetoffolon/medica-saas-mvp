package com.bisioneers.medica.billing.controller;

import com.bisioneers.medica.billing.security.*;
import com.bisioneers.medica.billing.security.MfaService.MfaSetupResult;
import com.bisioneers.medica.billing.security.MfaService.MfaStatus;
import com.bisioneers.medica.billing.security.MfaSessionStore.MfaSession;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints de Multi-Factor Authentication (TOTP).
 *
 * Setup/configuración (requieren JWT del usuario):
 *   GET  /api/auth/mfa/status   → Estado actual del usuario
 *   POST /api/auth/mfa/setup    → Genera secreto + URI para QR
 *   POST /api/auth/mfa/enable   → Activa MFA con primer código
 *   POST /api/auth/mfa/disable  → Desactiva MFA (requiere password + código)
 *
 * Verificación (pública, durante login):
 *   POST /api/auth/mfa/verify   → Completa el login con código TOTP
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private static final Logger log = LoggerFactory.getLogger(MfaController.class);

    private final MfaService mfaService;
    private final MfaSessionStore mfaSessionStore;
    private final JwtService jwtService;
    private final StaffUserRepository staffUserRepository;
    private final TenantRepository tenantRepository;
    private final long accessTokenMinutes;

    public MfaController(MfaService mfaService,
                         MfaSessionStore mfaSessionStore,
                         JwtService jwtService,
                         StaffUserRepository staffUserRepository,
                         TenantRepository tenantRepository,
                         @Value("${security.jwt.expiration-minutes:60}") long accessTokenMinutes) {
        this.mfaService = mfaService;
        this.mfaSessionStore = mfaSessionStore;
        this.jwtService = jwtService;
        this.staffUserRepository = staffUserRepository;
        this.tenantRepository = tenantRepository;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    /** Estado MFA del usuario actual */
    @GetMapping("/status")
    public ResponseEntity<MfaStatus> getStatus(
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        return ResponseEntity.ok(mfaService.getStatus(principal.getUserId()));
    }

    /**
     * Inicia el setup de MFA: genera secreto nuevo y retorna la URI
     * para el QR. MFA aún no se activa hasta POST /enable.
     */
    @PostMapping("/setup")
    public ResponseEntity<SetupResponse> setup(
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        MfaSetupResult result = mfaService.setup(principal.getUserId());
        return ResponseEntity.ok(new SetupResponse(result.secret(), result.otpAuthUri()));
    }

    /** Activa MFA verificando el primer código del authenticator */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, String>> enable(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @RequestBody EnableRequest request) {
        mfaService.enable(principal.getUserId(), request.code());
        log.info("MFA enabled for user {}", principal.getUsername());
        return ResponseEntity.ok(Map.of("message", "MFA activado correctamente"));
    }

    /** Desactiva MFA (requiere password + código) */
    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disable(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @RequestBody DisableRequest request) {
        mfaService.disable(principal.getUserId(), request.password(), request.code());
        log.info("MFA disabled for user {}", principal.getUsername());
        return ResponseEntity.ok(Map.of("message", "MFA desactivado"));
    }

    /**
     * Completa el login cuando el usuario tiene MFA activo.
     * Se invoca DESPUÉS del POST /api/auth/login que retornó mfaRequired:true.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest request) {
        MfaSession session = mfaSessionStore.consume(request.mfaSessionToken());
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Sesión MFA expirada o inválida. Inicia sesión nuevamente."
            ));
        }

        if (!mfaService.verifyCode(session.userId(), request.code())) {
            // Recrear sesión para que el usuario reintente sin volver a poner password
            String newToken = mfaSessionStore.createSession(
                    session.userId(), session.tenantId(), session.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Código incorrecto",
                    "mfaSessionToken", newToken
            ));
        }

        // MFA OK → cargar usuario y tenant para construir el Principal
        StaffUserEntity user = staffUserRepository.findById(session.userId()).orElse(null);
        if (user == null || !user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario no encontrado o inactivo"));
        }
        TenantEntity tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
        String tenantAlias = tenant != null ? tenant.getAlias() : "";

        StaffUserPrincipal principal = StaffUserPrincipal.fromEntity(user, tenantAlias);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        log.info("MFA login successful: user={}", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "expiresInSeconds", accessTokenMinutes * 60,
                "tokenType", "Bearer"
        ));
    }

    // ─── Request/Response DTOs ────────────────────────

    public record SetupResponse(String secret, String otpAuthUri) {}
    public record EnableRequest(@NotBlank String code) {}
    public record DisableRequest(@NotBlank String password, @NotBlank String code) {}
    public record VerifyRequest(@NotBlank String mfaSessionToken, @NotBlank String code) {}
}