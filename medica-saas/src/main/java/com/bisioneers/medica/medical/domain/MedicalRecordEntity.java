package com.bisioneers.medica.medical.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa una entrada en el historial clínico del paciente.
 * En MVP: texto libre.
 * Fase 2: campos estructurados, templates, etc.
 */
@Entity
@Table(name = "medical_record",
       indexes = {
           @Index(name = "idx_record_tenant_patient", columnList = "tenant_id,patient_id,record_date"),
           @Index(name = "idx_record_tenant_appt", columnList = "tenant_id,appointment_id")
       })
public class MedicalRecordEntity extends TenantScopedEntity {

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
     * ID de la cita relacionada (opcional)
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "appointment_id", columnDefinition = "BINARY(16)")
    private UUID appointmentId;

    /**
     * Fecha y hora del registro médico
     */
    @Column(name = "record_date", nullable = false)
    private LocalDateTime recordDate;

    /**
     * Tipo de registro
     * Valores: CONSULTATION, TREATMENT, FOLLOW_UP, LAB_RESULT, NOTE
     */
    @Column(name = "record_type", nullable = false, length = 30)
    private String recordType = "CONSULTATION";

    /**
     * Título breve del registro
     */
    @Column(length = 200)
    private String title;

    /**
     * Contenido del registro (texto libre en MVP)
     * Incluye: motivo consulta, hallazgos, diagnóstico, tratamiento, indicaciones
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Diagnóstico (texto libre)
     */
    @Column(length = 500)
    private String diagnosis;

    /**
     * Tratamiento aplicado (texto libre)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String treatment;

    /**
     * Indicaciones al paciente
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String instructions;

    /**
     * Si este registro ha sido firmado/cerrado por el médico
     */
    @Column(name = "signed", nullable = false)
    private boolean signed = false;

    /**
     * Si el registro está visible para el paciente en su portal
     */
    @Column(name = "patient_visible", nullable = false)
    private boolean patientVisible = false;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (recordDate == null) recordDate = LocalDateTime.now();
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

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(UUID appointmentId) {
        this.appointmentId = appointmentId;
    }

    public LocalDateTime getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDateTime recordDate) {
        this.recordDate = recordDate;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public boolean isSigned() {
        return signed;
    }

    public void setSigned(boolean signed) {
        this.signed = signed;
    }

    public boolean isPatientVisible() {
        return patientVisible;
    }

    public void setPatientVisible(boolean patientVisible) {
        this.patientVisible = patientVisible;
    }
}
