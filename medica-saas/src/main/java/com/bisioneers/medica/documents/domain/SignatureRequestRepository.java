package com.bisioneers.medica.documents.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignatureRequestRepository extends JpaRepository<SignatureRequestEntity, UUID> {

	Optional<SignatureRequestEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	/** Historial completo de intentos de firma del documento, más reciente primero. */
	List<SignatureRequestEntity> findByTenantIdAndPatientDocumentIdOrderByIdDesc(
			UUID tenantId, UUID patientDocumentId);

	/**
	 * Hay request PENDING activa para este documento.
	 * Si retorna Optional.empty(), se puede crear una nueva.
	 */
	@Query("SELECT r FROM SignatureRequestEntity r " +
			"WHERE r.tenantId = :tenantId AND r.patientDocumentId = :docId " +
			"AND r.status = com.bisioneers.medica.documents.domain.SignatureRequestStatus.PENDING")
	Optional<SignatureRequestEntity> findPendingByDocument(
			@Param("tenantId") UUID tenantId,
			@Param("docId") UUID docId);

	/**
	 * Lookup por hash del token. Usado en el endpoint público.
	 * NOTA: no se valida tenantId aquí porque el endpoint público no lo conoce.
	 * El service compara contra el token original (hash) y obtiene el tenant
	 * del registro.
	 */
	Optional<SignatureRequestEntity> findByTokenHashAndStatus(
			String tokenHash, SignatureRequestStatus status);

	/**
	 * Requests PENDING ya expiradas, para limpieza por scheduler.
	 */
	@Query("SELECT r FROM SignatureRequestEntity r " +
			"WHERE r.status = com.bisioneers.medica.documents.domain.SignatureRequestStatus.PENDING " +
			"AND r.expiresAt < :now")
	List<SignatureRequestEntity> findExpired(@Param("now") Instant now);
}
