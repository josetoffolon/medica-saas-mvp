package com.bisioneers.medica.appointment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
     * Verificar si hay conflicto de horario (para evitar choques).
     * 
     * PROBLEMA CORREGIDO:
     * La query anterior usaba FUNCTION('DATE_ADD', a.scheduledAt, a.durationMinutes, 'MINUTE')
     * que NO es JPQL válido y falla en MySQL. La función DATE_ADD en JPQL requiere sintaxis
     * específica del dialecto que Hibernate no resuelve correctamente.
     * 
     * SOLUCIÓN: Native query con MySQL DATE_ADD nativo + parámetro excludeId
     * para poder excluir la cita actual durante updates.
     * 
     * Lógica de overlap: dos rangos [A_start, A_end) y [B_start, B_end) se solapan
     * si A_start < B_end AND B_start < A_end.
     * 
     * - Cita nueva:     [:startTime, :endTime)
     * - Cita existente: [a.scheduled_at, a.scheduled_at + a.duration_minutes)
     */
    @Query(value =
        "SELECT COUNT(*) FROM appointment a " +
        "WHERE a.tenant_id = :tenantId " +
        "AND a.status NOT IN ('CANCELLED', 'NO_SHOW') " +
        "AND a.scheduled_at < :endTime " +
        "AND DATE_ADD(a.scheduled_at, INTERVAL a.duration_minutes MINUTE) > :startTime " +
        "AND (:excludeId IS NULL OR a.id != :excludeId)",
        nativeQuery = true
    )
    Long countConflicts(
        @Param("tenantId") byte[] tenantId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("excludeId") byte[] excludeId
    );

    /**
     * Sobrecarga sin excludeId (para crear citas nuevas).
     * Usa default method para no duplicar la query.
     */
    default boolean hasConflict(UUID tenantId, LocalDateTime startTime, LocalDateTime endTime) {
        return countConflicts(uuidToBytes(tenantId), startTime, endTime, null) > 0;
    }

    /**
     * Con excludeId (para actualizar citas existentes sin conflicto consigo misma).
     */
    default boolean hasConflictExcluding(UUID tenantId, LocalDateTime startTime, 
                                          LocalDateTime endTime, UUID excludeId) {
        return countConflicts(uuidToBytes(tenantId), startTime, endTime, uuidToBytes(excludeId)) > 0;
    }

    /**
     * Convierte UUID a byte[] BINARY(16) para native queries.
     * Necesario porque las native queries no usan el JdbcTypeCode mapping de JPA.
     */
    private static byte[] uuidToBytes(UUID uuid) {
        if (uuid == null) return null;
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i]     = (byte) (msb >>> (56 - i * 8));
            bytes[i + 8] = (byte) (lsb >>> (56 - i * 8));
        }
        return bytes;
    }

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
           "AND a.scheduledAt > :windowStart AND a.scheduledAt < :windowEnd")
    List<AppointmentEntity> findPendingReminder24h(
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    /**
     * Obtener citas que necesitan recordatorio de 2h
     */
    @Query("SELECT a FROM AppointmentEntity a WHERE a.status = 'SCHEDULED' " +
           "AND a.reminder2hSent = false " +
           "AND a.scheduledAt > :windowStart AND a.scheduledAt < :windowEnd")
    List<AppointmentEntity> findPendingReminder2h(
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );
    
    /**
     * Obtener Appointment filtrados con ID y TenantId
     */
    Optional<AppointmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
