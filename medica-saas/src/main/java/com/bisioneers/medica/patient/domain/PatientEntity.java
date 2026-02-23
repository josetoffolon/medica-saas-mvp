package com.bisioneers.medica.patient.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Representa un paciente en el sistema.
 * Cada paciente pertenece a un tenant (médico).
 */
@Entity
@Table(name = "patient",
       indexes = {
           @Index(name = "idx_patient_tenant_email", columnList = "tenant_id,email"),
           @Index(name = "idx_patient_tenant_document", columnList = "tenant_id,document_number"),
           @Index(name = "idx_patient_tenant_active", columnList = "tenant_id,active")
       })
public class PatientEntity extends TenantScopedEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Nombre completo del paciente
     */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /**
     * Email del paciente (opcional)
     */
    @Column(length = 160)
    private String email;

    /**
     * Teléfono principal
     */
    @Column(length = 20)
    private String phone;

    /**
     * Teléfono secundario/emergencia (opcional)
     */
    @Column(name = "secondary_phone", length = 20)
    private String secondaryPhone;

    /**
     * Tipo de documento (ej: "DNI", "PASAPORTE", "CEDULA")
     */
    @Column(name = "document_type", length = 20)
    private String documentType;

    /**
     * Número de documento
     */
    @Column(name = "document_number", length = 50)
    private String documentNumber;

    /**
     * Fecha de nacimiento
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Género (M, F, X)
     */
    @Column(length = 1)
    private String gender;

    /**
     * Dirección (opcional)
     */
    @Column(length = 500)
    private String address;

    /**
     * Observaciones generales (alergias, condiciones previas, etc.)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Si el paciente está activo (no eliminado lógicamente)
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Si el paciente ha firmado consentimiento para fotos
     */
    @Column(name = "photo_consent", nullable = false)
    private boolean photoConsent = false;

    /**
     * Si el paciente ha firmado consentimiento para datos personales
     */
    @Column(name = "data_consent", nullable = false)
    private boolean dataConsent = false;

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getSecondaryPhone() {
        return secondaryPhone;
    }

    public void setSecondaryPhone(String secondaryPhone) {
        this.secondaryPhone = secondaryPhone;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPhotoConsent() {
        return photoConsent;
    }

    public void setPhotoConsent(boolean photoConsent) {
        this.photoConsent = photoConsent;
    }

    public boolean isDataConsent() {
        return dataConsent;
    }

    public void setDataConsent(boolean dataConsent) {
        this.dataConsent = dataConsent;
    }
}
