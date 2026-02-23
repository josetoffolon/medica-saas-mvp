package com.bisioneers.medica.appointment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para crear una nueva cita
 */
public record CreateAppointmentRequest(
    @NotNull(message = "El ID del paciente es requerido")
    UUID patientId,
    
    UUID serviceId,
    
    @NotNull(message = "La fecha y hora son requeridas")
    @Future(message = "La fecha debe ser futura")
    LocalDateTime scheduledAt,
    
    @Min(value = 15, message = "La duración mínima es 15 minutos")
    @Max(value = 480, message = "La duración máxima es 480 minutos (8 horas)")
    Integer durationMinutes,
    
    @Size(max = 500)
    String reason,
    
    String staffNotes,
    String patientNotes
) {}
