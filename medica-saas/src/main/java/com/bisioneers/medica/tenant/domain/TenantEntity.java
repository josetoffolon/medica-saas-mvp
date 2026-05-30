package com.bisioneers.medica.tenant.domain;

import com.bisioneers.medica.billing.audit.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Representa un Tenant (Médico individual o Clínica).
 * En el modelo actual: 1 Tenant = 1 Médico
 */
@Entity
@Table(name = "tenant",
       indexes = {
           @Index(name = "idx_tenant_alias", columnList = "alias", unique = true),
           @Index(name = "idx_tenant_email", columnList = "contact_email")
       })
public class TenantEntity extends AuditedEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Alias único del tenant (ej: "dr-garcia", "clinica-bella")
     * Se usa para URLs amigables y referencias públicas
     */
    @Column(nullable = false, unique = true, length = 50)
    private String alias;

    /**
     * Nombre del médico o clínica (para mostrar)
     */
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    /**
     * Email de contacto principal
     */
    @Column(name = "contact_email", nullable = false, length = 160)
    private String contactEmail;

    /**
     * Teléfono de contacto (opcional)
     */
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /**
     * Dirección de la clínica/consultorio (opcional)
     */
    @Column(length = 500)
    private String address;

    /**
     * Timezone del tenant (ej: "America/Panama")
     * Usado para agendamiento y notificaciones
     */
    @Column(nullable = false, length = 50)
    private String timezone = "America/Panama";

    /**
     * Si el tenant está activo (puede operar en el sistema)
     * Diferente al estado de suscripción
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Configuración JSON opcional (para features/settings específicos)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String settings;
    
    /**
     * Duración del link de firma remota en horas.
     * Default: 24 horas.
     * Rango sensato: 1 a 168 horas (1 semana).
     *
     * Configurable por ADMIN desde Configuración de la clínica.
     */
    @Column(name = "signature_link_hours", nullable = false)
    private int signatureLinkHours = 24;

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

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }
    
    public int getSignatureLinkHours() { 
    	return signatureLinkHours; 
    }
    public void setSignatureLinkHours(int h) { 
    	this.signatureLinkHours = h;
    }
}
