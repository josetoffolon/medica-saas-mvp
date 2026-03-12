package com.bisioneers.medica.billing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
	/**

	- Encuentra suscripciones activas que vencen entre windowStart y windowEnd.
	 */
	@Query("SELECT s FROM SubscriptionEntity s WHERE s.status = 'ACTIVE' " +
			"AND s.currentPeriodEnd > :windowStart " +
			"AND s.currentPeriodEnd < :windowEnd")
	List<SubscriptionEntity> findExpiringBetween(
			@Param("windowStart") Instant windowStart,
			@Param("windowEnd") Instant windowEnd);

}
