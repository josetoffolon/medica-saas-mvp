package com.bisioneers.medica.billing.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Registra el TenantAwareTransactionManager como el PlatformTransactionManager
 * principal de la aplicación.
 *
 * Esto reemplaza el JpaTransactionManager por defecto de Spring Boot.
 * La única diferencia es que en doBegin() se activa automáticamente
 * el filtro Hibernate de tenant.
 */
@Configuration
public class TenantJpaConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        TenantAwareTransactionManager tm = new TenantAwareTransactionManager();
        tm.setEntityManagerFactory(emf);
        return tm;
    }
}
