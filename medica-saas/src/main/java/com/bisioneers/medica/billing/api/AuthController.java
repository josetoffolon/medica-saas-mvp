package com.bisioneers.medica.billing.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bisioneers.medica.billing.security.JwtService;
import com.bisioneers.medica.billing.security.StaffUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        StaffUserPrincipal principal = (StaffUserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);

        List<String> roles = principal.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .toList();

        LoginResponse response = new LoginResponse(
            token,
            "Bearer",
            principal.getTenantId().toString(),
            principal.getUserId().toString(),
            roles
        );

        return ResponseEntity.ok(response);
    }
}
