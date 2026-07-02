# Arquitectura — Medica SaaS (Backend)

> **Estado:** este documento refleja el sistema **tal como está implementado**, no el plan
> original del MVP. Sustituye a la versión previa, que describía como "fase futura"
> cosas que ya están en producción (JWT, MFA, notificaciones, storage en la nube,
> firmas electrónicas, consentimientos versionados).

Plataforma SaaS **multi-tenant** para clínicas de medicina estética. Un tenant = un
médico/clínica. Objetivo de capacidad: ~100 médicos.

---

## 1. Stack

| Capa | Tecnología |
|------|-----------|
| Lenguaje / runtime | Java 21 |
| Framework | Spring Boot 3.5.x (Spring MVC, Spring Security, Spring Data JPA) |
| ORM | Hibernate 6 / JPA |
| Base de datos | MySQL 8 |
| Migraciones | **Flyway** (`ddl-auto=validate`) |
| Auth | JWT (HS256) + MFA TOTP opcional |
| Pagos | Paguelo Fácil (Enlace de Pago / LinkDeamon + webhook + verificación server-to-server) |
| Storage de archivos | Local / Cloudflare R2 (S3-compatible) / Híbrido |
| Notificaciones | SMTP (email) + Twilio (WhatsApp), con fallback a log |
| PDF | OpenHTMLToPDF (generación) + Apache PDFBox (firma) |
| Frontend | Angular 17+ SPA (repositorio aparte) |

---

## 2. Vista de alto nivel

```
[ Angular SPA (:4200) ]
        │  HTTPS + JWT Bearer
        ▼
[ Nginx (proxy / TLS) ]
        │
        ▼
[ Spring Boot API (Tomcat embebido, :8080) ]
        │
        ├── Cadena de filtros de seguridad (ver §4)
        ├── Servicios de dominio
        │
        ▼
[ MySQL 8 ]          [ Cloudflare R2 ]        [ Paguelo Fácil / Twilio / SMTP ]
  (datos)              (fotos, PDFs)            (integraciones externas)
```

---

## 3. Multi-tenancy

**Modelo:** columna `tenant_id` (BINARY(16), UUID) en toda tabla de negocio.

El aislamiento se hace en **tres capas defensivas**:

1. **Filtro Hibernate automático.** `TenantScopedEntity` define un `@Filter` que añade
   `WHERE tenant_id = :tenantId` a las queries. Se activa en **cada transacción** vía
   `TenantAwareTransactionManager.doBegin()`, que lee el `TenantContext` (ThreadLocal
   poblado por `TenantContextFilter` desde el JWT).

   > Reemplaza al viejo enfoque AOP, que solo interceptaba `@Transactional` explícitos
   > y dejaba escapar las transacciones internas de Spring Data (findById, save…).

2. **Consultas por tenant.** **Regla de proyecto (#12):** el `@Filter` **no** aplica a
   `findById()` (carga por PK). Para cargar una entidad tenant-scoped por id se usa
   **siempre** `findByIdAndTenantId(id, tenantId)`, nunca `findById(id)` + chequeo manual.
   Una entidad de otro tenant es así indistinguible de una inexistente (no se filtra su
   existencia por el mensaje de error).

3. **Inmutabilidad de `tenant_id`.** `@PreUpdate` en `TenantScopedEntity` impide cambiar
   el `tenant_id` de una entidad existente (movería datos entre tenants).

**Excepciones intencionales** (operan sin `TenantContext`, ven todos los tenants): jobs
programados, webhooks de pago, y el lookup por token público de firma. Están documentadas
en el código.

---

## 4. Cadena de seguridad (orden de filtros)

```
Request
  → BearerTokenAuthenticationFilter (Spring)         valida el JWT
  → StaffJwtAuthenticationConverter                  Jwt → StaffUserPrincipal (TenantAware)
  → TenantContextFilter                              setea TenantContext (ThreadLocal) desde el principal
  → SubscriptionEnforcementFilter                    bloquea si la suscripción no está activa (402)
  → Controller
```

### JWT
- HS256, secreto obligatorio vía `JWT_SECRET` (**fail-fast**: la app no arranca sin él, y
  valida ≥32 bytes).
- Access token (60 min) + refresh token (7 días), cada uno con `jti` único.
- Claims: `sub` (email), `tenantId`, `userId`, `tenantAlias`, `roles`, `type`.
- **Blocklist persistente** (`revoked_token` en MySQL): logout y rotación de refresh
  revocan por `jti`; sobreviven a reinicios. Cache en memoria delante de la BD para el
  hot path de validación.

### MFA (TOTP, opcional por usuario)
- RFC 6238 (HMAC-SHA1, ventana 30s, 6 dígitos), compatible con Google Authenticator/Authy.
- Secreto **cifrado con AES-256-GCM** en BD (llave vía `MFA_ENCRYPTION_KEY`).
- Flujo login con MFA: `POST /login` devuelve `mfaRequired:true` + `mfaSessionToken` →
  `POST /mfa/verify` con el código completa el login.
- **Anti fuerza bruta:** lockout por email+IP (`LoginAttemptService`) + contador de
  intentos por sesión MFA (máx. 5).

### Roles
`ADMIN`, `MEDICO`, `RECEPCION`, `ASISTENTE`. Se aplican con `@PreAuthorize` a nivel de
método. Regla protegida: no se puede desactivar al último `ADMIN` de un tenant.

---

## 5. Módulos de dominio

| Módulo | Responsabilidad |
|--------|-----------------|
| `tenant` | Perfil del tenant, settings (JSON: horarios de atención), gestión de staff |
| `billing` + `security` | Auth, JWT, MFA, suscripciones, pagos, filtros de seguridad, tenancy |
| `patient` | Ficha del paciente (datos, contacto de emergencia, consentimientos) |
| `appointment` | Agenda, validación de horario laboral, anti-solape, recordatorios |
| `medical` | Historial clínico (firmable) y fotos médicas (antes/después) |
| `service` | Catálogo de servicios/tratamientos (+ catálogo público) |
| `consent` | Plantillas de consentimiento **versionadas** (DRAFT→PUBLISHED→ARCHIVED) |
| `documents` | Documentos del paciente + **firma electrónica** (presencial y remota) |
| `notification` | Router email/WhatsApp/log |
| `audit` | Log de auditoría (AOP) de acciones sensibles |

---

## 6. Suscripciones y facturación

**Modelo:** cada tenant paga una suscripción mensual. El `SubscriptionEnforcementFilter`
bloquea el acceso (HTTP 402) si la suscripción no está vigente.

**Estados:** `ACTIVE` · `GRACE_PERIOD` (vencida pero dentro de los días de gracia) ·
`PAST_DUE` · `INACTIVE` · `NONE`. En grace period se deja pasar con headers de aviso
(`X-Subscription-Status`, `X-Grace-Period-End`) para que el SPA muestre un banner.

**Optimización:** el estado se calcula de **una** lectura (`SubscriptionStatusSnapshot`) y
se cachea con TTL corto (`SubscriptionStatusCache`), porque el filtro corre en cada request.
El cache se invalida al activar un pago.

### Flujo de pago (Paguelo Fácil)

```
1. POST /api/billing/checkout    → crea tx PENDING, genera link de pago (LinkDeamon)
2. El paciente/médico paga en el portal de PF
3. Return URL (/billing/return)  → SOLO COSMÉTICO: muestra el estado ya persistido
4. Webhook (/api/billing/webhook/paguelofacil) → DISPARADOR, no fuente de verdad
5. markAsPaid → VERIFICA server-to-server con PF (queryActivities) antes de activar
6. Polling job reconcilia PENDING cuyo webhook no llegó (mismo markAsPaid verificado)
```

**Principio de seguridad:** ni el return ni el webhook se creen por sí solos. La
activación **solo** ocurre si PF confirma el pago server-to-server (`isOperationPaid`),
con validación de monto e idempotencia. Punto único de confianza: `markAsPaid`.

Cada evento de pago (webhook/return/checkout/error) se guarda como fila en
`payment_event` (historial consultable, sin crecer sin límite).

---

## 7. Consentimientos y firma electrónica

- **Plantillas versionadas** (`consent`): una plantilla tiene N versiones; solo una
  `PUBLISHED` es la vigente. `PUBLISHED`/`ARCHIVED` son **inmutables** (garantizado por
  `@PreUpdate`). El HTML se **sanitiza con Jsoup**; las variables `{{x.y}}` se resuelven
  con valores del paciente escapados como entidades HTML.
- **Documentos del paciente** (`documents`): se generan desde una versión PUBLISHED,
  guardando un **snapshot inmutable** del HTML renderizado. Ciclo:
  `DRAFT → READY_TO_SIGN → SIGNED → ARCHIVED`.
- **Firma:** presencial (mismo dispositivo del staff) o remota (link con token opaco
  de 256 bits, guardado como SHA-256, enviado por WhatsApp/email). El PDF firmado lleva
  un **certificado de firma** con hash SHA-256 de integridad (conforme a la Ley 51 de
  2008 de Panamá sobre firma electrónica).

---

## 8. Storage de archivos

Interfaz `MediaStorageService` con tres implementaciones seleccionables por
`medica.storage.type`:

- `local` — filesystem (dev).
- `s3` — Cloudflare R2 (bucket privado, URLs presignadas 5 min).
- `hybrid` — router: archivos nuevos → R2, archivos viejos → local.

Las fotos médicas y los PDFs firmados nunca se sirven con URL pública permanente: se
generan URLs de acceso temporales (R2) o vía endpoint autenticado (local).

---

## 9. Jobs programados

| Job | Frecuencia | Función |
|-----|-----------|---------|
| `AppointmentReminderJob` | 10 min | Recordatorios de cita 24h y 2h antes |
| `BillingPollingJob` | 5 min | Reconcilia pagos PENDING + expira links vencidos |
| `SubscriptionExpiryNotificationJob` | 24 h | Avisa a admins de suscripciones por vencer |
| `SignatureService.expireOldRequests` | 1 h | Expira solicitudes de firma vencidas |
| Limpiezas | varía | Blocklist de tokens, sesiones MFA, cache de suscripción |

---

## 10. Migraciones (Flyway)

`ddl-auto=validate` — Hibernate **no** modifica el schema, solo valida. Todo cambio de
schema es una migración versionada en `src/main/resources/db/migration/`.

| Versión | Contenido |
|---------|-----------|
| V1 | Baseline (schema extraído de la BD existente) |
| V2 | Tabla `payment_event` |
| V3 | Constraints `unique` de paciente (email/documento por tenant) |
| V4 | Índices del job de recordatorios |
| V5 | Tabla `revoked_token` |

> **Regla:** nunca modificar entidades esperando que el schema se refleje solo. Cada
> cambio → nueva migración `V{n}__descripcion.sql`.

---

## 11. Decisiones de diseño confirmadas

- Multi-tenant desde el día 1; **tenant = médico individual**.
- Auditoría obligatoria de cambios en historial, fotos, citas y datos sensibles.
- Agenda con duración por servicio o libre, horarios por día, anti-solape (lock pesimista).
- Historial clínico en texto libre (MVP); fotos antes/después con consentimiento requerido.
- Notificaciones: email + WhatsApp (Twilio), 24h y 2h antes.
- Solo el staff agenda citas.
- Pagos: por ahora solo la suscripción del médico (cobro al paciente = fase futura).
