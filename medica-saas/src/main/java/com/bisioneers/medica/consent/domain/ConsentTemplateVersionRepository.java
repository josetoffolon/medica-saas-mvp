package com.bisioneers.medica.consent.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentTemplateVersionRepository
extends JpaRepository<ConsentTemplateVersionEntity, UUID> {

	/**
	 * Lista de versiones validando tenant (mejora #6).
	 */
	@Query("SELECT v FROM ConsentTemplateVersionEntity v " +
			"WHERE v.templateId = :templateId AND v.tenantId = :tenantId " +
			"ORDER BY v.versionNumber DESC")
	List<ConsentTemplateVersionEntity> findByTemplateIdAndTenantIdOrderByVersionNumberDesc(
			@Param("templateId") UUID templateId,
			@Param("tenantId") UUID tenantId);

	/**
	 * Última versión validando tenant (mejora #6).
	 */
	@Query("SELECT v FROM ConsentTemplateVersionEntity v " +
			"WHERE v.templateId = :templateId AND v.tenantId = :tenantId " +
			"ORDER BY v.versionNumber DESC LIMIT 1")
	Optional<ConsentTemplateVersionEntity> findLatestByTemplateIdAndTenantId(
			@Param("templateId") UUID templateId,
			@Param("tenantId") UUID tenantId);

	/**
	 * Para auto-incrementar version_number con seguridad.
	 * Se invoca DENTRO de una transacción con lock pesimista sobre el template.
	 */
	@Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM ConsentTemplateVersionEntity v " +
			"WHERE v.templateId = :templateId")
	int findMaxVersionNumber(@Param("templateId") UUID templateId);

	/**
	 * Buscar versión validando tenant (mejora #6).
	 */
	Optional<ConsentTemplateVersionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	/**
	 * Contar versiones para listings (evita cargar todas).
	 */
	long countByTemplateId(UUID templateId);

	/**
	 * Query batch para evitar N+1 en el listado de templates (mejora #1).
	 *
	 * Retorna una proyección plana con: templateId, currentVersionNumber, totalVersions.
	 * Se invoca una sola vez por lista, en vez de 2 queries por template.
	 */
	@Query("""
			    SELECT new com.bisioneers.medica.consent.domain.TemplateVersionStats(
			        t.id,
			        cv.versionNumber,
			        (SELECT COUNT(v) FROM ConsentTemplateVersionEntity v WHERE v.templateId = t.id)
			    )
			    FROM ConsentTemplateEntity t
			    LEFT JOIN ConsentTemplateVersionEntity cv
			        ON cv.id = t.currentVersionId AND cv.tenantId = t.tenantId
			    WHERE t.tenantId = :tenantId
			""")
	List<TemplateVersionStats> findStatsByTenantId(@Param("tenantId") UUID tenantId);
}
