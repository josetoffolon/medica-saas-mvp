package com.bisioneers.medica.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, UUID> {
	
	Optional<PaymentTransactionEntity> findByProviderRef(String providerRef);

    List<PaymentTransactionEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
    
    /**
     * Listar transacciones del tenant ordenadas por fecha desc.
     * Hibernate filter de tenant ya aplica, pero pasamos tenantId
     * explícito para asegurar el WHERE en la query nativa de Spring Data.
     */
    Page<PaymentTransactionEntity> findByTenantIdOrderByCreatedAtDesc(
            UUID tenantId, Pageable pageable);

    /**
     * Buscar una transacción del tenant.
     * Retorna Optional vacío si no existe O si pertenece a otro tenant
     * (evita leak entre tenants en el endpoint /api/billing/transactions/{id}).
     */
    java.util.Optional<PaymentTransactionEntity> findByIdAndTenantId(
            UUID id, UUID tenantId);

}
