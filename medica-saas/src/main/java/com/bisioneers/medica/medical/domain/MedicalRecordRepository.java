package com.bisioneers.medica.medical.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecordEntity, UUID> {
    
    /**
     * Obtener historial de un paciente ordenado por fecha
     */
    Page<MedicalRecordEntity> findByTenantIdAndPatientIdOrderByRecordDateDesc(
        UUID tenantId, UUID patientId, Pageable pageable);
    
    /**
     * Obtener registros visibles para el paciente
     */
    List<MedicalRecordEntity> findByTenantIdAndPatientIdAndPatientVisibleTrueOrderByRecordDateDesc(
        UUID tenantId, UUID patientId);
    
    /**
     * Obtener registro asociado a una cita
     */
    List<MedicalRecordEntity> findByTenantIdAndAppointmentId(UUID tenantId, UUID appointmentId);
}
