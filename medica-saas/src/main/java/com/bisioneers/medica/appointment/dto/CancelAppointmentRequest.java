package com.bisioneers.medica.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para cancelar cita
 */
public record CancelAppointmentRequest(
    @NotBlank @Size(max = 500) String reason
) {}