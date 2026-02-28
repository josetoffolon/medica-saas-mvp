package com.bisioneers.medica.billing.tenant;

import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import java.util.UUID;

/**
 * TransactionManager custom que habilita el filtro Hibernate de tenant
 * automáticamente al inicio de CADA transacción.
 *
 * ¿POR QUÉ REEMPLAZA AL AOP?
 *
 * El anterior HibernateTenantFilterAspect usaba:
 *   @Around("@annotation(Transactional)")
 *
 * Esto solo interceptaba métodos con @Transactional EXPLÍCITO.
 * Pero Spring Data JPA crea transacciones INTERNAS para sus queries
 * (findById, findAll, save, etc.) que NO pasan por el AOP.
 *
 * Resultado: queries sin @Transactional explícito devolvían datos
 * de TODOS los tenants → data leak silencioso.
 *
 * SOLUCIÓN: Override doBegin() del JpaTransactionManager.
 * TODA transacción (explícita o implícita de Spring Data) pasa
 * por aquí, así que el filtro se activa SIEMPRE.
 *
 * Flujo:
 *   1. Spring Data o @Transactional inician transacción
 *   2. doBegin() → super.doBegin() crea el EntityManager
 *   3. doBegin() → lee TenantContext.getTenantId()
 *   4. doBegin() → habilita el filtro Hibernate con ese tenantId
 *   5. TODA query ejecutada en esa transacción solo ve datos del tenant
 *   6. Al terminar la transacción, el Session se cierra y el filtro muere con él
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareTransactionManager.class);

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 1. Dejar que Spring cree el EntityManager y lo vincule al thread
        super.doBegin(transaction, definition);

        // 2. Verificar si hay un tenant en el contexto
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Sin tenant → operación de sistema (billing jobs, webhooks, etc.)
            // No activar filtro, queries ven todos los tenants (intencional)
            return;
        }

        // 3. Obtener el EntityManager que super.doBegin() acaba de crear
        try {
            EntityManagerHolder emHolder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(getEntityManagerFactory());

            if (emHolder == null) {
                log.warn("No EntityManagerHolder found after doBegin - tenant filter not enabled");
                return;
            }

            EntityManager em = emHolder.getEntityManager();
            Session session = em.unwrap(Session.class);

            // 4. Habilitar el filtro Hibernate con el tenantId actual
            Filter filter = session.enableFilter("tenantFilter");
            filter.setParameter("tenantId", tenantId);

            log.trace("Tenant filter enabled: tenantId={}", tenantId);

        } catch (Exception e) {
            // No romper la transacción si algo falla al activar el filtro
            // Pero sí loguearlo como ERROR porque indica un problema serio
            log.error("Failed to enable tenant filter for tenantId={}: {}", tenantId, e.getMessage());
        }
    }
}
