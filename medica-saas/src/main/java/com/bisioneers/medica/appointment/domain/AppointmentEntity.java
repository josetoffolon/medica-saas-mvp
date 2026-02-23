package com.bisioneers.medica.appointment.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa una cita médica agendada.
 * Incluye información del paciente, servicio, horario y estado.
 */
@Entity
@Table(name = "appointment",
       indexes = {
           @Index(name = "idx_appt_tenant_datetime", columnList = "tenant_id,scheduled_at"),
           @Index(name = "idx_appt_tenant_patient", columnList = "tenant_id,patient_id"),
           @Index(name = "idx_appt_tenant_status", columnList = "tenant_id,status"),
           @Index(name = "idx_appt_tenant_date_range", columnList = "tenant_id,scheduled_at,status")
       })
public class AppointmentEntity extends TenantScopedEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * ID del paciente
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "patient_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID patientId;

    /**
     * ID del servicio/tratamiento (opcional, puede ser consulta general)
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "service_id", columnDefinition = "BINARY(16)")
    private UUID serviceId;

    /**
     * Fecha y hora de la cita (en timezone del tenant)
     * Se guarda como LocalDateTime para facilitar queries por fecha/hora
     */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /**
     * Duración en minutos (heredada del servicio o custom)
     */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    /**
     * Estado de la cita
     * Valores: SCHEDULED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW
     */
    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    /**
     * Motivo de la cita (texto libre)
     */
    @Column(length = 500)
    private String reason;

    /**
     * Notas adicionales del staff
     */
    @Lob
    @Column(name = "staff_notes", columnDefinition = "TEXT")
    private String staffNotes;

    /**
     * Notas del paciente (lo que menciona al agendar)
     */
    @Lob
    @Column(name = "patient_notes", columnDefinition = "TEXT")
    private String patientNotes;

    /**
     * Si se envió recordatorio 24h antes
     */
    @Column(name = "reminder_24h_sent", nullable = false)
    private boolean reminder24hSent = false;

    /**
     * Si se envió recordatorio 2h antes
     */
    @Column(name = "reminder_2h_sent", nullable = false)
    private boolean reminder2hSent = false;

    /**
     * Timestamp de cuando se envió confirmación
     */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /**
     * Timestamp de cuando se canceló
     */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * Razón de cancelación
     */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /**
     * ID de cita padre (para citas recurrentes - fase 2)
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "parent_appointment_id", columnDefinition = "BINARY(16)")
    private UUID parentAppointmentId;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    // Getters y Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStaffNotes() {
        return staffNotes;
    }

    public void setStaffNotes(String staffNotes) {
        this.staffNotes = staffNotes;
    }

    public String getPatientNotes() {
        return patientNotes;
    }

    public void setPatientNotes(String patientNotes) {
        this.patientNotes = patientNotes;
    }

    public boolean isReminder24hSent() {
        return reminder24hSent;
    }

    public void setReminder24hSent(boolean reminder24hSent) {
        this.reminder24hSent = reminder24hSent;
    }

    public boolean isReminder2hSent() {
        return reminder2hSent;
    }

    public void setReminder2hSent(boolean reminder2hSent) {
        this.reminder2hSent = reminder2hSent;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public UUID getParentAppointmentId() {
        return parentAppointmentId;
    }

    public void setParentAppointmentId(UUID parentAppointmentId) {
        this.parentAppointmentId = parentAppointmentId;
    }
}
