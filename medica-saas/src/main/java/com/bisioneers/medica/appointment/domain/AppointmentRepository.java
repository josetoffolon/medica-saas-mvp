package com.bisioneers.medica.appointment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {
    
    /**
     * Obtener citas de un tenant en un rango de fechas
     */
    @Query("SELECT a FROM AppointmentEntity a WHERE a.tenantId = :tenantId " +
           "AND a.scheduledAt >= :start AND a.scheduledAt < :end " +
           "AND a.status NOT IN ('CANCELLED') " +
           "ORDER BY a.scheduledAt")
    List<AppointmentEntity> findByTenantAndDateRange(
        @Param("tenantId") UUID tenantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    /**
     * Verificar si hay conflicto de horario (para evitar choques)
     */
    @Query("SELECT COUNT(a) > 0 FROM AppointmentEntity a WHERE a.tenantId = :tenantId " +
           "AND a.status NOT IN ('CANCELLED', 'NO_SHOW') " +
           "AND ((a.scheduledAt < :endTime AND " +
           "FUNCTION('DATE_ADD', a.scheduledAt, a.durationMinutes, 'MINUTE') > :startTime))")
    boolean hasConflict(
        @Param("tenantId") UUID tenantId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Obtener citas de un paciente específico
     */
    Page<AppointmentEntity> findByTenantIdAndPatientIdOrderByScheduledAtDesc(
        UUID tenantId, UUID patientId, Pageable pageable);
    
    /**
     * Obtener próximas citas (para recordatorios)
     */
    @Query("SELECT a FROM AppointmentEntity a WHERE a.tenantId = :tenantId " +
           "AND a.status = 'SCHEDULED' " +
           "AND a.scheduledAt > :now AND a.scheduledAt < :until " +
           "ORDER BY a.scheduledAt")
    List<AppointmentEntity> findUpcomingForReminders(
        @Param("tenantId") UUID tenantId,
        @Param("now") LocalDateTime now,
        @Param("until") LocalDateTime until
    );
    
    /**
     * Obtener citas que necesitan recordatorio de 24h
     */
    @Query("SELECT a FROM AppointmentEntity a WHERE a.status = 'SCHEDULED' " +
           "AND a.reminder24hSent = false " +
           "AND a.scheduledAt > :now AND a.scheduledAt < :windowEnd")
    List<AppointmentEntity> findPendingReminder24h(
        @Param("now") LocalDateTime now,
        @Param("windowEnd") LocalDateTime windowEnd
    );
    
    /**
     * Obtener citas que necesitan recordatorio de 2h
     */
    @Query("SELECT a FROM AppointmentEntity a WHERE a.status = 'SCHEDULED' " +
           "AND a.reminder2hSent = false " +
           "AND a.scheduledAt > :now AND a.scheduledAt < :windowEnd")
    List<AppointmentEntity> findPendingReminder2h(
        @Param("now") LocalDateTime now,
        @Param("windowEnd") LocalDateTime windowEnd
    );
}
