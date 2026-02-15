package com.bisioneers.medica.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, UUID> {
	
	Optional<PaymentTransactionEntity> findByProviderRef(String providerRef);

    List<PaymentTransactionEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);

}
