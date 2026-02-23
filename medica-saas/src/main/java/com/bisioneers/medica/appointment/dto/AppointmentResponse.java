package com.bisioneers.medica.appointment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de respuesta con datos de la cita
 */
public record AppointmentResponse(
    UUID id,
    UUID patientId,
    UUID serviceId,
    LocalDateTime scheduledAt,
    int durationMinutes,
    String status,
    String reason,
    String staffNotes,
    String patientNotes,
    boolean reminder24hSent,
    boolean reminder2hSent
) {
    public static AppointmentResponse from(com.bisioneers.medica.appointment.domain.AppointmentEntity entity) {
        return new AppointmentResponse(
            entity.getId(),
            entity.getPatientId(),
            entity.getServiceId(),
            entity.getScheduledAt(),
            entity.getDurationMinutes(),
            entity.getStatus(),
            entity.getReason(),
            entity.getStaffNotes(),
            entity.getPatientNotes(),
            entity.isReminder24hSent(),
            entity.isReminder2hSent()
        );
    }
}
