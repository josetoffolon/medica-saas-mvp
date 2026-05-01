# Resumen Ejecutivo — Medica SaaS MVP

**Fecha:** Mayo 2026  
**Estado:** MVP funcional listo para validación con primeros clientes

---

## ¿Qué es Medica SaaS?

Plataforma SaaS multi-tenant para clínicas y médicos estéticos. Permite gestionar pacientes, citas, historia clínica y facturación desde un único sistema, con acceso por suscripción mensual.

---

## Problema que resuelve

Los consultorios médico-estéticos operan con agendas en papel, WhatsApp o herramientas genéricas que no cubren sus necesidades clínicas (fotos médicas antes/después, historia clínica, consentimientos). Medica SaaS centraliza todo en una plataforma diseñada para este sector.

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 + Spring Boot 3.5.9 |
| Frontend | Angular 17+ (standalone components, signals) |
| Base de datos | MySQL + JPA/Hibernate |
| Autenticación | JWT + MFA (TOTP / Google Authenticator) |
| Pagos | PagueloFacil (checkout + webhooks) |
| Notificaciones | Email (SMTP) + WhatsApp (Twilio) |
| Resiliencia | Resilience4j (circuit breaker) |

---

## Funcionalidades implementadas

### Gestión clínica
- **Pacientes** — Alta, edición, historial completo, búsqueda
- **Citas** — Agenda con flujo de estados (pendiente → confirmada → completada / no-show / cancelada), validación de horarios y recordatorios automáticos
- **Historia clínica** — Notas médicas y carga de fotografías médicas con comparación antes/después
- **Servicios** — Catálogo de tratamientos facturables por clínica

### Seguridad y control de acceso
- **Multi-tenancy** — Aislamiento completo de datos por clínica
- **Roles de usuario** — ADMIN, MEDICO, RECEPCION, ASISTENTE con permisos granulares
- **Autenticación JWT** — Token de acceso (60 min) + refresh token (7 días)
- **MFA** — Autenticación en dos pasos compatible con Google Authenticator
- **Auditoría** — Registro automático de todos los cambios con usuario y timestamp

### Billing y suscripción
- **Checkout integrado** con PagueloFacil
- **Período de gracia** de 5 días tras vencimiento antes de bloquear acceso
- **Webhooks** de confirmación de pago con validación de firma
- **Historial de transacciones** consultable por el tenant
- **Bloqueo automático** (HTTP 402) cuando la suscripción está inactiva

### Notificaciones
- Router multi-canal inteligente: Email y WhatsApp según preferencia del paciente
- Jobs programados (Quartz) para recordatorios de citas y alertas de suscripción próxima a vencer

---

## Arquitectura en resumen

```
Angular 17 SPA
      ↕ (JWT en cada request)
Spring Boot REST API  ←→  MySQL
      ↕
  PagueloFacil / Twilio / SMTP
```

- El frontend es un SPA servible desde Nginx o empaquetado dentro del JAR de Spring Boot.
- El backend expone ~15 grupos de endpoints REST bajo `/api/*` y `/billing/*`.
- Cada tenant tiene sus datos completamente aislados mediante un contexto de hilo (ThreadLocal).

---

## Lo que falta para producción

1. **Infraestructura** — Configurar servidor/nube, dominio y certificado SSL
2. **Variables de entorno** — Credenciales de PagueloFacil, Twilio y SMTP en producción
3. **Storage de fotos** — Migrar de almacenamiento local a S3/GCS para escalar
4. **CI/CD** — Pipeline de despliegue automatizado
5. **Pruebas de carga** — Validar rendimiento con múltiples tenants simultáneos

---

## Resumen

El MVP está **completo funcionalmente**: autenticación, multi-tenancy, gestión clínica, billing y notificaciones están operativos. El siguiente paso es desplegar en un entorno real y arrancar el proceso de adquisición de los primeros clientes pagos.
