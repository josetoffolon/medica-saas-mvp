package com.bisioneers.medica.patient.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	 * Búsqueda multi-campo: nombre, email, teléfono o documento.
	 *
	 * CAMBIO: Reemplaza searchByName que solo buscaba por fullName.
	 * Ahora busca en 4 campos simultáneamente con un solo término.
	 *
	 * Ejemplo: buscar "jose" encuentra:
	 *   - Paciente con fullName "José García"
	 *   - Paciente con email "jose@gmail.com"
	 *   - (no matchea teléfono/documento pero el frontend puede buscar "6000-1234")
	 */
	@Query("SELECT p FROM PatientEntity p WHERE p.tenantId = :tenantId " +
			"AND p.active = true " +
			"AND (LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"  OR LOWER(p.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"  OR p.phone LIKE CONCAT('%', :search, '%') " +
			"  OR p.documentNumber LIKE CONCAT('%', :search, '%'))")
	Page<PatientEntity> searchMultiField(@Param("tenantId") UUID tenantId,
			@Param("search") String search,
			Pageable pageable);

	/**
	 * Contar pacientes activos por tenant
	 */
	long countByTenantIdAndActiveTrue(UUID tenantId);

}