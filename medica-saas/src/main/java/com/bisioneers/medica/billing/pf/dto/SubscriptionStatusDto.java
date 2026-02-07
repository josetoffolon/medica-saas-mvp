package com.bisioneers.medica.billing.pf.dto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionStatusDto(
        UUID tenantId,
        String status,          // ACTIVE | INACTIVE | PAST_DUE | NONE
        Instant periodStart,
        Instant periodEnd,
        Instant serverTime
) {}
