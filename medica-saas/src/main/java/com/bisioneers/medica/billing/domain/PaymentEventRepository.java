package com.bisioneers.medica.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {

	/** Historial de eventos de una transacción, más reciente primero. */
	List<PaymentEventEntity> findByTenantIdAndTransactionIdOrderByCreatedAtDesc(
			UUID tenantId, UUID transactionId);

	/** Último evento de una transacción (para TransactionDto.extractFailedReason). */
	PaymentEventEntity findFirstByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
}