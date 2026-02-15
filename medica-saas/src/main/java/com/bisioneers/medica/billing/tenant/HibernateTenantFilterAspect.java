package com.bisioneers.medica.billing.tenant;

import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Aspect
@Component
public class HibernateTenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object enableTenantFilter(ProceedingJoinPoint pjp) throws Throwable {

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return pjp.proceed();
        }

        Session session;
        try {
            session = entityManager.unwrap(Session.class);
        } catch (Exception ex) {
            // fallback: si no hay sesión Hibernate disponible, continúa sin filtro
            return pjp.proceed();
        }

        boolean enabledHere = false;
        Filter f = session.getEnabledFilter("tenantFilter");
        if (f == null) {
            f = session.enableFilter("tenantFilter");
            enabledHere = true;
        }

        f.setParameter("tenantId", tenantId);

        try {
            return pjp.proceed();
        } finally {
            if (enabledHere) {
                session.disableFilter("tenantFilter");
            }
        }
    }
}