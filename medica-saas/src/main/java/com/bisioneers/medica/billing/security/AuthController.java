package com.bisioneers.medica.billing.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.bisioneers.medica.billing.api.LoginResponse;

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
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

      Authentication authentication = authManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.email(), request.password())
      );

      StaffUserPrincipal principal = (StaffUserPrincipal) authentication.getPrincipal();
      String token = jwtService.generateToken(principal);

      List<String> roles = principal.getAuthorities().stream()
          .map(a -> a.getAuthority())
          .toList();

      return ResponseEntity.ok(new LoginResponse(
          token,
          "Bearer",
          principal.getTenantId().toString(),
          principal.getUserId().toString(),
          roles
      ));
  }

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
}