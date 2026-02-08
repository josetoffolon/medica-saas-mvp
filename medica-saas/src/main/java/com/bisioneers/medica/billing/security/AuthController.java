package com.bisioneers.medica.billing.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final JwtService jwtService;

  public AuthController(AuthenticationManager authManager, JwtService jwtService) {
    this.authManager = authManager;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req) {
    Authentication auth = authManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.email(), req.password())
    );

    String token = jwtService.issueStaffToken(auth);
    return ResponseEntity.ok(new TokenResponse(token, "Bearer"));
  }

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
  public record TokenResponse(String accessToken, String tokenType) {}
}

