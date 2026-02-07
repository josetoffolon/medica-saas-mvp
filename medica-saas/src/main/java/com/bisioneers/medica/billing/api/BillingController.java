package com.bisioneers.medica.billing.api;

import com.bisioneers.medica.billing.BillingService;
import com.bisioneers.medica.billing.domain.TenantAware;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    // En producción: tenantId y tenantAlias deben salir del JWT (TenantContext), no del body.
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(Authentication auth,
                                      @RequestBody CheckoutRequest req) {
      UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();

      // seguridad: NO aceptes tenantId del body
      var dto = billingService.startCheckout(tenantId, req.tenantAlias());
      return ResponseEntity.ok(dto);
    }

    public record CheckoutRequest(String tenantAlias) {}
    
}

