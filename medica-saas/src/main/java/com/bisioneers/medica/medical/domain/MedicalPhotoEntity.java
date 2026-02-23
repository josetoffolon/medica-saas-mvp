package com.bisioneers.medica.medical.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa una foto médica (antes/después de tratamiento).
 * Almacena metadata, el archivo se guarda en filesystem o S3.
 */
@Entity
@Table(name = "medical_photo",
       indexes = {
           @Index(name = "idx_photo_tenant_patient", columnList = "tenant_id,patient_id,captured_at"),
           @Index(name = "idx_photo_tenant_record", columnList = "tenant_id,medical_record_id"),
           @Index(name = "idx_photo_tenant_appt", columnList = "tenant_id,appointment_id")
       })
public class MedicalPhotoEntity extends TenantScopedEntity {

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
     * ID del registro médico relacionado (opcional)
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "medical_record_id", columnDefinition = "BINARY(16)")
    private UUID medicalRecordId;

    /**
     * ID de la cita relacionada (opcional)
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "appointment_id", columnDefinition = "BINARY(16)")
    private UUID appointmentId;

    /**
     * Tipo de foto: BEFORE, AFTER, PROGRESS, OTHER
     */
    @Column(name = "photo_type", nullable = false, length = 20)
    private String photoType = "BEFORE";

    /**
     * Path o URL de la foto almacenada
     * Ej: "patients/abc-123/photos/2024-01-15_before.jpg"
     */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /**
     * Nombre original del archivo
     */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /**
     * MIME type (image/jpeg, image/png)
     */
    @Column(name = "mime_type", length = 50)
    private String mimeType;

    /**
     * Tamaño del archivo en bytes
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Fecha y hora de captura de la foto
     */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /**
     * Área anatómica fotografiada (ej: "Rostro frontal", "Perfil izquierdo")
     */
    @Column(name = "anatomical_area", length = 100)
    private String anatomicalArea;

    /**
     * Descripción o notas sobre la foto
     */
    @Column(length = 500)
    private String notes;

    /**
     * Si el paciente ha dado consentimiento para esta foto específica
     */
    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    /**
     * Si la foto es visible para el paciente en su portal
     */
    @Column(name = "patient_visible", nullable = false)
    private boolean patientVisible = false;

    /**
     * ID de la foto "pareja" (ej: la foto AFTER tiene referencia a su BEFORE)
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "paired_photo_id", columnDefinition = "BINARY(16)")
    private UUID pairedPhotoId;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (capturedAt == null) capturedAt = LocalDateTime.now();
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

    public UUID getMedicalRecordId() {
        return medicalRecordId;
    }

    public void setMedicalRecordId(UUID medicalRecordId) {
        this.medicalRecordId = medicalRecordId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(UUID appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getPhotoType() {
        return photoType;
    }

    public void setPhotoType(String photoType) {
        this.photoType = photoType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public String getAnatomicalArea() {
        return anatomicalArea;
    }

    public void setAnatomicalArea(String anatomicalArea) {
        this.anatomicalArea = anatomicalArea;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isConsentGiven() {
        return consentGiven;
    }

    public void setConsentGiven(boolean consentGiven) {
        this.consentGiven = consentGiven;
    }

    public boolean isPatientVisible() {
        return patientVisible;
    }

    public void setPatientVisible(boolean patientVisible) {
        this.patientVisible = patientVisible;
    }

    public UUID getPairedPhotoId() {
        return pairedPhotoId;
    }

    public void setPairedPhotoId(UUID pairedPhotoId) {
        this.pairedPhotoId = pairedPhotoId;
    }
}
