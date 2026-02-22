# Medica SaaS - Frontend (Angular 17+)

Frontend SPA para el sistema SaaS médico-estético **Medica SaaS**.
Diseñado para conectarse al backend Spring Boot 3.5.9 + JWT.

## Requisitos

- **Node.js** 18+ (recomendado 20 LTS)
- **npm** 9+
- Backend Spring Boot corriendo en `http://localhost:8080`

## Setup rápido

```bash
# 1. Instalar dependencias
npm install

# 2. Iniciar en modo desarrollo (con proxy al backend)
npm start

# → Abre http://localhost:4200
# → Todas las requests /api/* se redirigen a localhost:8080
```

## Proxy de desarrollo

El archivo `proxy.conf.json` redirige automáticamente las llamadas al backend:

| Frontend (Angular)          | Backend (Spring Boot)          |
|-----------------------------|--------------------------------|
| `http://localhost:4200/api/*` | `http://localhost:8080/api/*` |
| `http://localhost:4200/billing/*` | `http://localhost:8080/billing/*` |

No necesitas configurar CORS en desarrollo.

## Estructura del proyecto

```
src/app/
├── core/                         # Servicios singleton, interceptors, guards
│   ├── auth/                     # AuthService, TokenService, LoginComponent
│   │   ├── auth.service.ts       # Login/logout
│   │   ├── token.service.ts      # JWT decode, storage, signals
│   │   └── login/                # Login page component
│   ├── guards/                   # Route guards
│   │   ├── auth.guard.ts         # → Requiere autenticación
│   │   ├── guest.guard.ts        # → Redirige si ya autenticado
│   │   ├── role.guard.ts         # → Requiere rol específico (ADMIN, MEDICO...)
│   │   └── subscription.guard.ts # → Verifica suscripción activa
│   └── interceptors/             # HTTP interceptors
│       ├── jwt.interceptor.ts    # → Auto-inyecta Bearer token
│       ├── subscription.interceptor.ts # → Captura HTTP 402
│       └── error.interceptor.ts  # → Captura HTTP 401 → logout
│
├── features/                     # Feature modules (lazy-loaded)
│   ├── dashboard/                # Panel principal
│   ├── appointments/             # Agenda de citas (↔ appointment-service)
│   │   ├── components/
│   │   └── appointments.routes.ts
│   ├── patients/                 # Pacientes + historial (↔ patient-service)
│   │   ├── components/
│   │   └── patients.routes.ts
│   ├── billing/                  # Suscripción + checkout PF (↔ billing-service)
│   │   ├── components/
│   │   ├── services/
│   │   └── billing.routes.ts
│   └── settings/                 # Config del tenant (solo ADMIN)
│
├── shared/                       # Componentes y utilidades compartidas
│   ├── components/
│   │   └── layout/               # Shell: sidebar + navbar + content
│   ├── models/                   # Interfaces TypeScript (mirror de DTOs Java)
│   └── pipes/
│
└── app.routes.ts                 # Routing principal con lazy loading
```

## Mapeo Backend ↔ Frontend

| Backend (Spring Boot)                     | Frontend (Angular)                    |
|-------------------------------------------|---------------------------------------|
| `SecurityConfig` + JWT                    | `jwtInterceptor` + `authGuard`        |
| `SubscriptionEnforcementFilter` (HTTP 402)| `subscriptionInterceptor` + `subscriptionGuard` |
| Roles: ADMIN, MEDICO, RECEPCION, ASISTENTE| `roleGuard` con `data.roles`          |
| `POST /api/auth/login` → JWT             | `AuthService.login()`                 |
| `GET /api/subscription/me`               | `BillingService.getSubscriptionStatus()` |
| `POST /api/billing/checkout` → PagueloFacil | `BillingService.startCheckout()`   |
| `TenantContext` (ThreadLocal)             | `TokenService.tenantId` (Signal)      |

## Build para producción

```bash
npm run build:prod
# Output en dist/medica-frontend/
```

### Despliegue con Spring Boot

Copia el contenido de `dist/medica-frontend/browser/` a `src/main/resources/static/`
del proyecto Spring Boot. Spring servirá el SPA automáticamente.

### Despliegue con Nginx (recomendado)

```nginx
server {
    listen 80;
    server_name tudominio.com;

    root /var/www/medica-frontend;
    index index.html;

    # SPA: todas las rutas van a index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy al backend
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /billing/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Tecnologías

- **Angular 17+** (standalone components, signals, new control flow)
- **Angular Material** (UI component library)
- **RxJS** (reactive programming)
- **TypeScript** (strict mode)
- **SCSS** (styles with design tokens)
