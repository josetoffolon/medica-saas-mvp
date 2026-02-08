package com.bisioneers.medica.billing.api;

import java.util.List;

public record LoginResponse(
    String accessToken,
    String tokenType,
    String tenantId,
    String userId,
    List<String> roles
) {
}
