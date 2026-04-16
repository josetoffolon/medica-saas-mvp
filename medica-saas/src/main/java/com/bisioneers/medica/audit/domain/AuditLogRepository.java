package com.bisioneers.medica.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

	/** Todos los logs del tenant, paginados, más recientes primero */
	Page<AuditLogEntity> findByTenantIdOrderByTimestampDesc(UUID tenantId, Pageable pageable);

	/** Filtrar por tipo de acción */
	Page<AuditLogEntity> findByTenantIdAndActionOrderByTimestampDesc(
			UUID tenantId, String action, Pageable pageable);

	/** Filtrar por tipo de entidad */
	Page<AuditLogEntity> findByTenantIdAndEntityTypeOrderByTimestampDesc(
			UUID tenantId, String entityType, Pageable pageable);

	/** Filtrar por usuario específico */
	Page<AuditLogEntity> findByTenantIdAndUserIdOrderByTimestampDesc(
			UUID tenantId, UUID userId, Pageable pageable);

	/** Historial de una entidad específica */
	List<AuditLogEntity> findByTenantIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
			UUID tenantId, String entityType, UUID entityId);

	/** Búsqueda combinada con filtros opcionales */
	@Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId " +
			"AND (:action IS NULL OR a.action = :action) " +
			"AND (:entityType IS NULL OR a.entityType = :entityType) " +
			"AND (:userId IS NULL OR a.userId = :userId) " +
			"AND (:startDate IS NULL OR a.timestamp >= :startDate) " +
			"AND (:endDate IS NULL OR a.timestamp <= :endDate) " +
			"ORDER BY a.timestamp DESC")
	Page<AuditLogEntity> findFiltered(
			@Param("tenantId") UUID tenantId,
			@Param("action") String action,
			@Param("entityType") String entityType,
			@Param("userId") UUID userId,
			@Param("startDate") Instant startDate,
			@Param("endDate") Instant endDate,
			Pageable pageable);

	/** Contar acciones por tipo (para dashboard de auditoría) */
	@Query("SELECT a.action, COUNT(a) FROM AuditLogEntity a " +
			"WHERE a.tenantId = :tenantId AND a.timestamp >= :since " +
			"GROUP BY a.action ORDER BY COUNT(a) DESC")
	List<Object[]> countByActionSince(@Param("tenantId") UUID tenantId,
			@Param("since") Instant since);
}
