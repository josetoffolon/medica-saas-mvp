package com.bisioneers.medica.patient.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<PatientEntity, UUID> {
    
    /**
     * Buscar pacientes activos por tenant
     */
    Page<PatientEntity> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);
    
    /**
     * Buscar paciente por email dentro de un tenant
     */
    Optional<PatientEntity> findByTenantIdAndEmail(UUID tenantId, String email);
    
    /**
     * Buscar paciente por documento dentro de un tenant
     */
    Optional<PatientEntity> findByTenantIdAndDocumentNumber(UUID tenantId, String documentNumber);
    
    /**
     * Búsqueda por nombre (contiene)
     */
    @Query("SELECT p FROM PatientEntity p WHERE p.tenantId = :tenantId " +
           "AND p.active = true " +
           "AND LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<PatientEntity> searchByName(@Param("tenantId") UUID tenantId, 
                                      @Param("search") String search, 
                                      Pageable pageable);
    
    /**
     * Contar pacientes activos por tenant
     */
    long countByTenantIdAndActiveTrue(UUID tenantId);
}
