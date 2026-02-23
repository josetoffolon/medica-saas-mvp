package com.bisioneers.medica.appointment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para actualizar una cita
 */
public record UpdateAppointmentRequest(
    @NotNull UUID patientId,
    UUID serviceId,
    @NotNull LocalDateTime scheduledAt,
    @Min(15) @Max(480) int durationMinutes,
    @Size(max = 500) String reason,
    String staffNotes,
    String patientNotes,
    @Pattern(regexp = "^(SCHEDULED|CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED|NO_SHOW)$")
    String status
) {}