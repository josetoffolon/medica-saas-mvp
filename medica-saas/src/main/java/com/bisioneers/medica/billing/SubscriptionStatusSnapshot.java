package com.bisioneers.medica.billing;

import java.time.Instant;

/**
 * Estado de suscripción derivado de UNA sola lectura de BD.
 *
 * Evita las 3 queries que antes hacía el SubscriptionEnforcementFilter
 * (isActive + isInGracePeriod + getGracePeriodEnd, cada una con su findById).
 *
 *  active        → el tenant puede usar el sistema (vigente o en gracia)
 *  inGracePeriod → vencido pero dentro de los días de gracia
 *  graceEnd      → cuándo termina la gracia (null si no aplica)
 *  status        → ACTIVE | GRACE_PERIOD | PAST_DUE | INACTIVE | NONE
 */
public record SubscriptionStatusSnapshot(
        boolean active,
        boolean inGracePeriod,
        Instant graceEnd,
        String status
) {}