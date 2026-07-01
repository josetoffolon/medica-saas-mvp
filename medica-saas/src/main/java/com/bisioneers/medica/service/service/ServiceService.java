package com.bisioneers.medica.service.service;

import com.bisioneers.medica.service.domain.ServiceEntity;
import com.bisioneers.medica.service.domain.ServiceRepository;
import com.bisioneers.medica.service.dto.ServiceDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de negocio para gestión del catálogo de servicios/tratamientos.
 *
 * NOTA: El TenantAwareTransactionManager activa automáticamente
 * el filtro Hibernate de tenant en cada transacción, por lo que
 * las queries con findByTenantId son doblemente seguras.
 */
@Service
public class ServiceService {

    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    // ─── CREATE ───────────────────────────────────────────────────────

    @Transactional
    public ServiceEntity create(UUID tenantId, CreateServiceRequest request) {
        ServiceEntity entity = new ServiceEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setDurationMinutes(request.durationMinutes());
        entity.setPrice(request.price());
        entity.setCurrency(request.currency());
        entity.setCategory(request.category());
        entity.setActive(request.active() != null ? request.active() : true);
        entity.setPublicVisible(request.publicVisible() != null ? request.publicVisible() : true);
        entity.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        entity.setRequiresPhotoConsent(
                request.requiresPhotoConsent() != null ? request.requiresPhotoConsent() : false);

        return serviceRepository.save(entity);
    }

    // ─── READ ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ServiceEntity getById(UUID tenantId, UUID serviceId) {
        return serviceRepository.findByIdAndTenantId(serviceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
    }

    /**
     * Todos los servicios activos del tenant (para agendamiento interno).
     */
    @Transactional(readOnly = true)
    public List<ServiceEntity> getActiveServices(UUID tenantId) {
        return serviceRepository.findByTenantIdAndActiveTrueOrderByDisplayOrderAsc(tenantId);
    }

    /**
     * Servicios activos y públicos (para portal público / landing page).
     */
    @Transactional(readOnly = true)
    public List<ServiceEntity> getPublicServices(UUID tenantId) {
        return serviceRepository.findByTenantIdAndActiveTrueAndPublicVisibleTrueOrderByDisplayOrderAsc(tenantId);
    }

    /**
     * Servicios activos filtrados por categoría.
     */
    @Transactional(readOnly = true)
    public List<ServiceEntity> getByCategory(UUID tenantId, String category) {
        return serviceRepository.findByTenantIdAndCategoryAndActiveTrueOrderByDisplayOrderAsc(
                tenantId, category);
    }

    /**
     * Categorías distintas del tenant (para filtros en el frontend).
     */
    @Transactional(readOnly = true)
    public List<String> getCategories(UUID tenantId) {
        return getActiveServices(tenantId).stream()
                .map(ServiceEntity::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    // ─── UPDATE ───────────────────────────────────────────────────────

    @Transactional
    public ServiceEntity update(UUID tenantId, UUID serviceId, UpdateServiceRequest request) {
        ServiceEntity existing = getById(tenantId, serviceId);

        if (request.name() != null) existing.setName(request.name());
        if (request.description() != null) existing.setDescription(request.description());
        if (request.durationMinutes() != null) existing.setDurationMinutes(request.durationMinutes());
        if (request.price() != null) existing.setPrice(request.price());
        if (request.currency() != null) existing.setCurrency(request.currency());
        if (request.category() != null) existing.setCategory(request.category());
        if (request.active() != null) existing.setActive(request.active());
        if (request.publicVisible() != null) existing.setPublicVisible(request.publicVisible());
        if (request.displayOrder() != null) existing.setDisplayOrder(request.displayOrder());
        if (request.requiresPhotoConsent() != null) existing.setRequiresPhotoConsent(request.requiresPhotoConsent());

        return serviceRepository.save(existing);
    }

    // ─── DELETE (soft) ────────────────────────────────────────────────

    @Transactional
    public void deactivate(UUID tenantId, UUID serviceId) {
        ServiceEntity existing = getById(tenantId, serviceId);
        existing.setActive(false);
        existing.setPublicVisible(false);
        serviceRepository.save(existing);
    }

    @Transactional
    public void activate(UUID tenantId, UUID serviceId) {
        ServiceEntity existing = getById(tenantId, serviceId);
        existing.setActive(true);
        serviceRepository.save(existing);
    }
}
