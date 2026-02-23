package com.bisioneers.medica.service.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    
    /**
     * Obtener servicios activos de un tenant ordenados por displayOrder
     */
    List<ServiceEntity> findByTenantIdAndActiveTrueOrderByDisplayOrderAsc(UUID tenantId);
    
    /**
     * Obtener servicios públicos y activos (para portal público)
     */
    List<ServiceEntity> findByTenantIdAndActiveTrueAndPublicVisibleTrueOrderByDisplayOrderAsc(UUID tenantId);
    
    /**
     * Obtener servicios por categoría
     */
    List<ServiceEntity> findByTenantIdAndCategoryAndActiveTrueOrderByDisplayOrderAsc(
        UUID tenantId, String category);
}
