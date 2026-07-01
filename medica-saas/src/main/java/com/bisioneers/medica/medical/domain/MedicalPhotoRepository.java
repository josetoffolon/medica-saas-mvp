package com.bisioneers.medica.medical.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalPhotoRepository extends JpaRepository<MedicalPhotoEntity, UUID> {
    
    /**
     * Obtener fotos de un paciente
     */
    List<MedicalPhotoEntity> findByTenantIdAndPatientIdOrderByCapturedAtDesc(
        UUID tenantId, UUID patientId);
    
    /**
     * Obtener fotos de un registro médico
     */
    List<MedicalPhotoEntity> findByTenantIdAndMedicalRecordId(
        UUID tenantId, UUID medicalRecordId);
    
    /**
     * Obtener fotos de una cita
     */
    List<MedicalPhotoEntity> findByTenantIdAndAppointmentId(
        UUID tenantId, UUID appointmentId);
    
    /**
     * Obtener pares de fotos (antes/después)
     */
    @Query("SELECT p FROM MedicalPhotoEntity p WHERE p.tenantId = :tenantId " +
           "AND p.patientId = :patientId " +
           "AND (p.pairedPhotoId IS NOT NULL OR " +
           "p.id IN (SELECT p2.pairedPhotoId FROM MedicalPhotoEntity p2 WHERE p2.tenantId = :tenantId)) " +
           "ORDER BY p.capturedAt DESC")
    List<MedicalPhotoEntity> findPairedPhotos(
        @Param("tenantId") UUID tenantId,
        @Param("patientId") UUID patientId);
    
    /**
     * Obtener MedicalPhoto filtrados con ID y TenantId
     */
    Optional<MedicalPhotoEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
