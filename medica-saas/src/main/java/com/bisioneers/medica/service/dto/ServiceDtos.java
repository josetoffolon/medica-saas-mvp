package com.bisioneers.medica.service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTOs para el módulo de Servicios/Catálogo.
 */
public final class ServiceDtos {

    private ServiceDtos() {}

    // ─── Request DTOs ─────────────────────────────────────────────────

    public record CreateServiceRequest(
            @NotBlank @Size(max = 200)
            String name,

            String description,

            @Min(5) @Max(480)
            int durationMinutes,

            @DecimalMin("0.00")
            BigDecimal price,

            @Size(max = 3)
            String currency,

            @Size(max = 100)
            String category,

            Boolean active,

            Boolean publicVisible,

            Integer displayOrder,

            Boolean requiresPhotoConsent
    ) {}

    public record UpdateServiceRequest(
            @Size(max = 200)
            String name,

            String description,

            @Min(5) @Max(480)
            Integer durationMinutes,

            @DecimalMin("0.00")
            BigDecimal price,

            @Size(max = 3)
            String currency,

            @Size(max = 100)
            String category,

            Boolean active,

            Boolean publicVisible,

            Integer displayOrder,

            Boolean requiresPhotoConsent
    ) {}

    // ─── Response DTOs ────────────────────────────────────────────────

    public record ServiceResponse(
            UUID id,
            String name,
            String description,
            int durationMinutes,
            BigDecimal price,
            String currency,
            boolean active,
            boolean publicVisible,
            String category,
            int displayOrder,
            boolean requiresPhotoConsent
    ) {}

    /**
     * Versión reducida para listados públicos (sin info interna).
     */
    public record PublicServiceResponse(
            UUID id,
            String name,
            String description,
            int durationMinutes,
            BigDecimal price,
            String currency,
            String category
    ) {}
}