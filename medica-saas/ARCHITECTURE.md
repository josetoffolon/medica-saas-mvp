# Arquitectura propuesta (MVP SaaS médico-estética)

## Objetivo
Definir una arquitectura cliente–servidor basada en **Spring Boot + Tomcat**, con persistencia relacional y preparada para una futura **migración multi‑cloud** (on‑prem, AWS, OCI, GCP). Esta arquitectura soporta un modelo **multi‑tenant (médico = tenant)** con cobro de suscripción mensual, y habilita las funcionalidades clínicas básicas del MVP.

## Resumen de la arquitectura

```
[ Web Client (SPA) ]
         |
         v
[ Spring Boot API (Tomcat embebido) ]
         |
         v
[ MySQL / MariaDB ]
```

**Servicios clave**
- Autenticación y autorización (Spring Security)
- Gestión de suscripciones y facturación
- Gestión de pacientes, citas y evolución
- Notificaciones (email / WhatsApp link / SMS opcional)

## Componentes y responsabilidades

### 1) Cliente (Frontend Web)
- SPA (React/Vue/Angular) o templates server-side (Thymeleaf) para MVP rápido.
- Funciones:
  - Agenda de citas
  - Historial médico y evolución con fotos
  - Portal de servicios/promociones
  - Pantalla de suscripción vencida + botón de renovación

### 2) Servidor (Spring Boot + Tomcat)
- **API REST** como capa principal.
- Tomcat embebido por defecto (compatible con despliegue WAR en Tomcat externo).
- Módulos recomendados:
  - `auth-service`: login, roles, permisos
  - `tenant-service`: resolución de tenant por request
  - `appointment-service`: agenda, citas
  - `patient-service`: ficha clínica, historial y evolución
  - `media-service`: manejo de fotos (antes/después)
  - `billing-service`: suscripciones, cobros, transacciones
  - `notification-service`: recordatorios y avisos

### 3) Persistencia (MySQL o alternativa gratuita)
- **MySQL** (actual) o **MariaDB** si se busca 100% open‑source.
- ORM: **JPA/Hibernate** (base actual) o **MyBatis** si se requiere SQL fino.
- Estrategia multi‑tenant:
  - **tenant_id en todas las tablas** (más simple y económica).
  - Filtro por tenant en repositorios / servicios.

### 4) Integraciones
- **Pago de suscripción**: proveedor actual (Paguelo Fácil) con webhooks.
- **Notificaciones**:
  - Email (SMTP)
  - WhatsApp (links directos en MVP, API oficial en fase posterior)
- **Almacenamiento de fotos**:
  - Local en MVP; migrable a S3/OCI Object Storage/GCS.

## Arquitectura lógica (nivel alto)

```
Cliente
   ↓
API Spring Boot (Tomcat)
   ↓
Security + Subscription Filter
   ↓
Servicios de dominio
   ↓
Persistencia (MySQL/MariaDB)
```

## Multi‑cloud: decisiones para facilitar migración
- **Contenerización**: Docker + Compose (local), Kubernetes en futuro.
- **Config externa**: variables de entorno + `application.yml` por perfil.
- **Storage desacoplado**: interfaz `MediaStorage` con implementación local y nube.
- **Base de datos**: MySQL/MariaDB administrada en cada proveedor.
- **Infra como código** (futuro): Terraform para AWS/OCI/GCP.

## Modelo de datos básico (MVP)
- **Tenant (médico)**
- **User/Staff** (roles: ADMIN, MEDICO, RECEPCION, ASISTENTE)
- **Patient**
- **Appointment**
- **MedicalRecord**
- **MedicalPhoto**
- **Subscription**
- **PaymentTransaction**

## Seguridad
- Autenticación con email + password + BCrypt.
- JWT para frontend (fase 2).
- Filtro de suscripción: bloquea endpoints si tenant no está activo.

## Pendientes para completar MVP (backend + frontend)
1. Login real y flujo de autenticación.
2. Frontend mínimo con manejo de suscripción vencida.
3. Webhook validado y dominio real.
4. Observabilidad básica (logs estructurados + manejo de errores global).

## Decisiones confirmadas (resumen de respuestas)
### A. Alcance del SaaS y multi-tenant
- **Multi-tenant desde el día 1** con médicos múltiples.
- **Tenant = médico individual** (no clínica agrupada).

### B. Roles, permisos y auditoría
- Roles requeridos: **Admin (dueño), Médico, Recepción, Asistente**.
- **Auditoría obligatoria** para cambios en historial, fotos, citas y datos sensibles.

### C. Agenda y reglas de citas
- Soporte para **duración por servicio (30/60 min) o duración libre**.
- **Horarios por día**, **bloqueos/feriados** y **citas recurrentes**.
- **Evitar choques por fecha/hora** (no se requiere recurso adicional por sala/cabina en MVP).

### D. Historial clínico y fotos
- Historial con **texto libre** en el MVP.
- Fotos “antes/después” con **2 fotos por visita** y **comparador lado a lado**.
- **Consentimiento del paciente** requerido para fotos y datos.

### E. Notificaciones
- Canales MVP: **Email** y **WhatsApp vía Twilio**.
- Envíos a **24h** y **2h** antes de la cita.

### F. Portal del paciente
- **Catálogo público** de servicios/promociones.
- Portal con login para que el paciente vea **sus citas**.
- **Solo el staff agenda** citas (el paciente no agenda directo en MVP).

### G. Pagos e integración con WhatsApp
- MVP: **solo suscripción del médico**.
- Cobro a paciente **fase siguiente**.
- **WhatsApp Business API** se deja para fase siguiente (no MVP).

### H. Infraestructura y despliegue
- MVP: **VPS Linux + Tomcat + MySQL**.
- Fase siguiente: migración a nube (AWS/Azure/OCI/GCP).
- Capacidad objetivo primer año: **100 médicos**, **40 pacientes por médico**.

### I. Tecnología
- Persistencia: **Hibernate/JPA**.
- Arquitectura: API REST con SPA recomendada (SSR opcional si acelera MVP).
