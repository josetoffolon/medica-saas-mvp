# Medica SaaS — Backend

Backend de una plataforma **SaaS multi-tenant** para clínicas de medicina estética.
Cada tenant es un médico/clínica independiente, con su propia agenda, pacientes,
historial clínico, consentimientos y suscripción.

> **Frontend:** Angular 17+ SPA — repositorio aparte
> (`medica-saas-frontend`). Ver §"Contrato para el frontend".

Para la visión completa de arquitectura, ver [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## Stack

Java 21 · Spring Boot 3.5 · MySQL 8 · Hibernate 6 / JPA · Flyway · Spring Security
(JWT + MFA TOTP) · Cloudflare R2 · Twilio · SMTP · OpenHTMLToPDF + PDFBox.

---

## Puesta en marcha (desarrollo local)

### Requisitos
- JDK 21
- MySQL 8 corriendo en `localhost:3306`
- Maven (o el wrapper `./mvnw`)

### 1. Base de datos

```sql
CREATE DATABASE medica_saas CHARACTER SET utf8mb4;
CREATE USER 'medica'@'localhost' IDENTIFIED BY 'tu_password';
GRANT ALL PRIVILEGES ON medica_saas.* TO 'medica'@'localhost';
```

No hace falta crear tablas a mano: **Flyway** las crea al arrancar (baseline + migraciones).

### 2. Variables de entorno obligatorias

| Variable | Descripción |
|----------|-------------|
| `DB_PASSWORD` | Password del usuario `medica` |
| `JWT_SECRET` | Secreto HS256, **≥32 bytes**. La app **no arranca sin él** |
| `MFA_ENCRYPTION_KEY` | Llave AES para cifrar secretos TOTP (Base64, 32 bytes) |

Genera los secretos:

```bash
export JWT_SECRET=$(openssl rand -base64 48)
export MFA_ENCRYPTION_KEY=$(openssl rand -base64 32)
export DB_PASSWORD=tu_password
```

Variables **opcionales** (integraciones; sin ellas el canal queda deshabilitado y cae a
log/no-op): `SMTP_USERNAME`, `SMTP_PASSWORD`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`,
`TWILIO_WHATSAPP_NUMBER`, `PAGUELOFACIL_ACCESS_TOKEN`, `PAGUELOFACIL_CCLW`,
`R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`.

### 3. Arrancar

```bash
# Perfil dev = logging SQL verboso (NUNCA usar en producción: vuelca datos sensibles)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`. El SPA de desarrollo corre en `http://localhost:4200`.

---

## Perfiles

| Perfil | Uso |
|--------|-----|
| *(base)* | Seguro por defecto: sin SQL en logs, storage R2 |
| `dev` | Logging SQL/binds verboso (solo local) |
| `prod` | Producción |

> **Importante:** el perfil `dev` activa el volcado de SQL y parámetros (incluye datos de
> pacientes y hashes). Nunca activarlo en producción.

---

## Estructura del proyecto

```
com.bisioneers.medica
├── billing/            Auth, JWT, MFA, suscripciones, pagos, tenancy, seguridad
│   ├── security/       JwtService, MfaService, StaffUserPrincipal, filtros, blocklist
│   ├── tenant/         TenantContext, TenantScopedEntity, TenantAwareTransactionManager
│   ├── webhook/        Webhook de Paguelo Fácil + validación
│   ├── pf/             Clientes de Paguelo Fácil (Link + Activities)
│   └── domain/         PaymentTransaction, PaymentEvent, Subscription
├── tenant/             Perfil del tenant + gestión de staff
├── patient/            Ficha del paciente
├── appointment/        Agenda, horarios, recordatorios
├── medical/            Historial clínico + fotos + storage
├── service/            Catálogo de servicios
├── consent/            Plantillas de consentimiento versionadas
├── documents/          Documentos del paciente + firma electrónica
├── notification/       Router email/WhatsApp/log
└── audit/              Auditoría vía AOP
```

Convención por módulo: `api`/`controller` (REST) · `service` (negocio) · `domain`
(entidad + repositorio) · `dto`.

---

## Conceptos clave para entender el backend

### Multi-tenancy (leer antes de tocar cualquier query)
Todo dato pertenece a un tenant. El aislamiento tiene tres capas: filtro Hibernate
automático, consultas `findByIdAndTenantId`, e inmutabilidad de `tenant_id`.

**Regla obligatoria:** para cargar una entidad tenant-scoped por id, usa
`repository.findByIdAndTenantId(id, tenantId)` — **nunca** `findById(id)`. El filtro
Hibernate no aplica a la carga por PK, así que `findById` puede devolver datos de otro
tenant. (Detalle en `TenantScopedEntity` y en `ARCHITECTURE.md §3`.)

### Autenticación
El principal en todo endpoint autenticado es `StaffUserPrincipal`, del que se obtiene
`getTenantId()`, `getUserId()`, roles, etc.:

```java
@GetMapping("/{id}")
public ResponseEntity<PatientResponse> getById(
        @AuthenticationPrincipal StaffUserPrincipal principal,
        @PathVariable UUID id) {
    return ResponseEntity.ok(
        PatientResponse.from(patientService.getById(principal.getTenantId(), id)));
}
```

### Migraciones (Flyway)
`ddl-auto=validate`: Hibernate no modifica el schema. Cualquier cambio de tabla/columna
es una migración nueva `V{n}__descripcion.sql` en `src/main/resources/db/migration/`.
Nunca modifiques una entidad esperando que el schema se actualice solo.

### Manejo de errores
`GlobalExceptionHandler` (único advice) traduce:
`IllegalArgumentException → 400` · `IllegalStateException → 409` ·
validación → 400 · resto → 500 (mensaje genérico, sin filtrar detalle interno).
Cuerpo uniforme: `{ timestamp, status, error, path }`.

---

## Contrato para el frontend

### Autenticación

```
POST /api/auth/register        Onboarding: crea tenant + admin. Devuelve tokens.
POST /api/auth/login           email+password. Si el usuario tiene MFA → { mfaRequired, mfaSessionToken }
POST /api/auth/mfa/verify      Completa login con código TOTP (público)
POST /api/auth/refresh         Rota el par de tokens (público)
POST /api/auth/logout          Revoca access + refresh
POST /api/auth/change-password
```

**Header en requests autenticados:** `Authorization: Bearer <accessToken>`.

**Flujo de refresh recomendado:** ante un 401, llamar a `/refresh` con el refresh token;
si también falla, redirigir a login. Los tokens rotan (el refresh usado se revoca).

**Flujo MFA:** si `/login` responde `mfaRequired:true`, guardar el `mfaSessionToken` y
pedir el código TOTP. Enviar a `/mfa/verify`. Ante código incorrecto se devuelve el
**mismo** `mfaSessionToken` con los intentos restantes; tras agotarlos (5) o si el 401 no
incluye token, volver a `/login`.

### Endpoints principales (todos bajo `Authorization: Bearer`)

```
Pacientes        /api/patients            CRUD + búsqueda + consentimientos
Citas            /api/appointments        CRUD + confirmar/cancelar/completar/no-show
Historial        /api/medical-records     CRUD + firmar/des-firmar
Fotos            /api/medical-photos      upload + pares antes/después
Servicios        /api/services            CRUD + /api/public/services/{alias} (catálogo público)
Consentimientos  /api/consent-templates   plantillas + versiones + preview
Documentos       /api/documents           generar + preparar + firmar
Firma pública    /api/public/sign/{token} (sin auth — solo el token autoriza)
Staff            /api/staff               gestión de usuarios (ADMIN)
Tenant           /api/tenant              perfil + settings (horarios)
Suscripción      /api/subscription/me     estado actual
Billing          /api/billing/checkout    iniciar pago
                 /api/billing/transactions historial
Auditoría        /api/audit               logs (ADMIN)
```

### Headers de suscripción a vigilar
El backend puede devolver, en cualquier response autenticado:
- `X-Subscription-Status: GRACE_PERIOD` + `X-Grace-Period-End: <ISO instant>` → mostrar
  banner de aviso de vencimiento.
- **HTTP 402** con `{ error: "subscription_inactive", checkoutUrl }` → suscripción
  expirada; redirigir al flujo de pago.

### Roles
`ADMIN`, `MEDICO`, `RECEPCION`, `ASISTENTE` — vienen en el claim `roles` del JWT.
El SPA debe usarlos para route guards; el backend los reaplica con `@PreAuthorize`.

---

## Backlog / notas de operación

- **Paguelo Fácil:** en sandbox, mantener `paguelofacil.webhook.require-amount-match=false`
  y la validación de IP desactivada hasta confirmar con PF (por correo) sus IPs de origen,
  el formato del `conditional` de consulta, y si el webhook propaga `PARM_1`. La activación
  de suscripción se apoya en la **verificación server-to-server**, no en el webhook.
- **Producción:** cambiar las URLs de PF de `sandbox.paguelofacil.com` a
  `secure.paguelofacil.com`.
- **Optimizaciones pendientes** (no bloqueantes): `@Cacheable` en `BusinessHoursService`,
  interfaz `Auditable` en vez de reflexión en `AuditAspect`, Specifications en la
  auditoría filtrada.

---

## Comandos útiles

```bash
./mvnw clean package                        # build + tests
./mvnw spring-boot:run                       # arrancar
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # con SQL verboso
```
