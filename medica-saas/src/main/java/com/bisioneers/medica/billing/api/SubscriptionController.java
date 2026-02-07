package com.bisioneers.medica.billing.api;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bisioneers.medica.billing.SubscriptionService;
import com.bisioneers.medica.billing.domain.TenantAware;
import com.bisioneers.medica.billing.pf.dto.SubscriptionStatusDto;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;

  public SubscriptionController(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @GetMapping("/me")
  public SubscriptionStatusDto myStatus(Authentication auth) {
    UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
    return subscriptionService.getStatus(tenantId);
  }
}