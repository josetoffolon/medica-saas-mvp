package com.bisioneers.medica.service.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representa un servicio o tratamiento ofrecido por el médico.
 * Ej: "Botox frontal", "Relleno labial", "Limpieza facial", etc.
 */
@Entity
@Table(name = "service",
       indexes = {
           @Index(name = "idx_service_tenant_active", columnList = "tenant_id,active"),
           @Index(name = "idx_service_tenant_public", columnList = "tenant_id,public_visible")
       })
public class ServiceEntity extends TenantScopedEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    /**
     * Nombre del servicio/tratamiento
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * Descripción del servicio (para portal público)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Duración estimada en minutos (para agendamiento)
     * Ej: 30, 60, 90
     */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 30;

    /**
     * Precio del servicio (opcional, puede no mostrarse)
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Moneda del precio
     */
    @Column(length = 3)
    private String currency;

    /**
     * Si el servicio está activo (disponible para agendar)
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Si el servicio es visible en el portal público
     */
    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible = true;

    /**
     * Categoría del servicio (ej: "Facial", "Corporal", "Inyectables")
     */
    @Column(length = 100)
    private String category;

    /**
     * Orden de visualización (menor = primero)
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /**
     * Requiere consentimiento especial de fotos
     */
    @Column(name = "requires_photo_consent", nullable = false)
    private boolean requiresPhotoConsent = false;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPublicVisible() {
        return publicVisible;
    }

    public void setPublicVisible(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isRequiresPhotoConsent() {
        return requiresPhotoConsent;
    }

    public void setRequiresPhotoConsent(boolean requiresPhotoConsent) {
        this.requiresPhotoConsent = requiresPhotoConsent;
    }
}
