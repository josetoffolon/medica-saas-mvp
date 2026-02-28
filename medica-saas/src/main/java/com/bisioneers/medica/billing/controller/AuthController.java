package com.bisioneers.medica.billing.controller;

import com.bisioneers.medica.billing.dto.AuthDtos.*;
import com.bisioneers.medica.billing.security.*;
import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador de autenticación completo.
 *
 * Endpoints:
 *   POST /api/auth/login          → Login con email/password → access + refresh tokens
 *   POST /api/auth/refresh        → Renueva tokens con refresh token válido
 *   POST /api/auth/logout         → Invalida access + refresh tokens (blocklist)
 *   POST /api/auth/change-password → Cambia contraseña del usuario autenticado
 *   POST /api/auth/register       → Onboarding: crea tenant + usuario admin
 *
 * CORRECCIONES vs versión anterior:
 * - Usa StaffUserEntity / StaffUserRepository (no Staff / StaffRepository que no existen)
 * - Usa TenantEntity de com.bisioneers.medica.tenant.domain (no billing.model)
 * - StaffUserEntity no tiene relación @ManyToOne con Tenant (solo tenantId UUID)
 * - StaffUserEntity no tiene firstName/lastName/phone/specialty
 * - Campo es 'enabled' no 'active'
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtDecoder jwtDecoder;
    private final TokenBlocklistService blocklistService;
    private final StaffUserRepository staffUserRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final long accessTokenMinutes;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtDecoder jwtDecoder,
            TokenBlocklistService blocklistService,
            StaffUserRepository staffUserRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            @Value("${security.jwt.expiration-minutes:60}") long accessTokenMinutes
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtDecoder = jwtDecoder;
        this.blocklistService = blocklistService;
        this.staffUserRepository = staffUserRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    // ─── LOGIN ────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        StaffUserPrincipal principal = (StaffUserPrincipal) auth.getPrincipal();

        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        log.info("Login successful: user={}, tenant={}", principal.getUsername(), principal.getTenantAlias());

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                refreshToken,
                accessTokenMinutes * 60
        ));
    }

    // ─── REFRESH TOKEN ────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            // 1. Decodificar y validar el refresh token (incluye check de blocklist)
            Jwt jwt = jwtDecoder.decode(request.refreshToken());

            // 2. Verificar que sea un refresh token, no un access token
            String tokenType = jwtService.extractTokenType(jwt);
            if (!"refresh".equals(tokenType)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Token proporcionado no es un refresh token"));
            }

            // 3. Cargar el usuario actual (puede haber sido desactivado)
            String email = jwt.getSubject();
            StaffUserEntity user = staffUserRepository.findByEmail(email).orElse(null);
            if (user == null || !user.isEnabled()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Usuario no encontrado o inactivo"));
            }

            // 4. Revocar el refresh token usado (rotation)
            blocklistService.revoke(jwt.getId(), jwt.getExpiresAt());

            // 5. Cargar tenant para obtener el alias
            TenantEntity tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            String tenantAlias = tenant != null ? tenant.getAlias() : "";

            // 6. Generar nuevo par de tokens
            StaffUserPrincipal principal = StaffUserPrincipal.fromEntity(user, tenantAlias);
            String newAccessToken = jwtService.generateAccessToken(principal);
            String newRefreshToken = jwtService.generateRefreshToken(principal);

            log.info("Token refreshed: user={}", email);

            return ResponseEntity.ok(new RefreshResponse(
                    newAccessToken,
                    newRefreshToken,
                    accessTokenMinutes * 60
            ));

        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token inválido o expirado"));
        }
    }

    // ─── LOGOUT ───────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) Map<String, String> body
    ) {
        // 1. Revocar el access token actual
        try {
            String accessTokenStr = authHeader.replace("Bearer ", "");
            Jwt accessJwt = jwtDecoder.decode(accessTokenStr);
            blocklistService.revoke(accessJwt.getId(), accessJwt.getExpiresAt());
        } catch (Exception e) {
            log.warn("Could not revoke access token during logout: {}", e.getMessage());
        }

        // 2. Revocar el refresh token si viene en el body
        if (body != null && body.containsKey("refreshToken")) {
            try {
                Jwt refreshJwt = jwtDecoder.decode(body.get("refreshToken"));
                blocklistService.revoke(refreshJwt.getId(), refreshJwt.getExpiresAt());
            } catch (Exception e) {
                log.warn("Could not revoke refresh token during logout: {}", e.getMessage());
            }
        }

        log.info("Logout: user={}", principal.getUsername());

        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }

    // ─── CAMBIO DE CONTRASEÑA ─────────────────────────────────────────

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        StaffUserEntity user = staffUserRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Usuario no encontrado"));
        }

        // Verificar contraseña actual
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Contraseña actual incorrecta"));
        }

        // Validar que la nueva sea diferente
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "La nueva contraseña debe ser diferente a la actual"));
        }

        // Actualizar
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        staffUserRepository.save(user);

        log.info("Password changed: user={}", principal.getUsername());

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    // ─── REGISTRO / ONBOARDING ────────────────────────────────────────

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterTenantRequest request) {

        // 1. Validar unicidad
        if (tenantRepository.existsByAlias(request.tenantAlias())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El alias de tenant '" + request.tenantAlias() + "' ya está en uso"));
        }
        if (staffUserRepository.findByEmail(request.adminEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El email '" + request.adminEmail() + "' ya está registrado"));
        }

        // 2. Crear Tenant
        TenantEntity tenant = new TenantEntity();
        tenant.setId(UUID.randomUUID());
        tenant.setDisplayName(request.tenantName());
        tenant.setAlias(request.tenantAlias());
        tenant.setActive(true);
        tenant = tenantRepository.save(tenant);

        // 3. Crear Staff admin
        StaffUserEntity admin = new StaffUserEntity();
        admin.setId(UUID.randomUUID());
        admin.setTenantId(tenant.getId());
        admin.setEmail(request.adminEmail());
        admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin = staffUserRepository.save(admin);

        // 4. Generar tokens para login automático post-registro
        StaffUserPrincipal principal = StaffUserPrincipal.fromEntity(admin, tenant.getAlias());
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        log.info("New tenant registered: tenant={}, admin={}", request.tenantAlias(), request.adminEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterTenantResponse(
                tenant.getId().toString(),
                admin.getId().toString(),
                accessToken,
                refreshToken,
                "Registro exitoso. Bienvenido a Medica."
        ));
    }
}
