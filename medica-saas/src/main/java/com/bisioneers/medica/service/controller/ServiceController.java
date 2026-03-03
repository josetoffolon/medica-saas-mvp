package com.bisioneers.medica.service.controller;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import com.bisioneers.medica.service.domain.ServiceEntity;
import com.bisioneers.medica.service.dto.ServiceDtos.*;
import com.bisioneers.medica.service.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller para gestión del catálogo de servicios/tratamientos.
 *
 * Endpoints autenticados (staff):
 *   GET    /api/services                → Listar servicios activos del tenant
 *   GET    /api/services/{id}           → Detalle de un servicio
 *   GET    /api/services/categories     → Categorías disponibles
 *   GET    /api/services?category=X     → Filtrar por categoría
 *   POST   /api/services                → Crear servicio (ADMIN)
 *   PUT    /api/services/{id}           → Actualizar servicio (ADMIN)
 *   PATCH  /api/services/{id}/deactivate → Desactivar (soft delete) (ADMIN)
 *   PATCH  /api/services/{id}/activate   → Reactivar (ADMIN)
 *
 * Endpoint público (sin JWT):
 *   GET    /api/public/services/{tenantAlias} → Catálogo público del tenant
 */
@RestController
@RequestMapping("/api")
public class ServiceController {

    private final ServiceService serviceService;
    private final com.bisioneers.medica.tenant.domain.TenantRepository tenantRepository;

    public ServiceController(ServiceService serviceService,
                             com.bisioneers.medica.tenant.domain.TenantRepository tenantRepository) {
        this.serviceService = serviceService;
        this.tenantRepository = tenantRepository;
    }

    // ─── ENDPOINTS AUTENTICADOS ───────────────────────────────────────

    /**
     * Listar servicios activos del tenant, opcionalmente filtrados por categoría.
     */
    @GetMapping("/services")
    public ResponseEntity<List<ServiceResponse>> list(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @RequestParam(required = false) String category
    ) {
        UUID tenantId = principal.getTenantId();

        List<ServiceEntity> services = (category != null && !category.isBlank())
                ? serviceService.getByCategory(tenantId, category)
                : serviceService.getActiveServices(tenantId);

        List<ServiceResponse> response = services.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Detalle de un servicio específico.
     */
    @GetMapping("/services/{id}")
    public ResponseEntity<ServiceResponse> getById(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id
    ) {
        ServiceEntity service = serviceService.getById(principal.getTenantId(), id);
        return ResponseEntity.ok(toResponse(service));
    }

    /**
     * Categorías disponibles para el tenant (para filtros del frontend).
     */
    @GetMapping("/services/categories")
    public ResponseEntity<List<String>> getCategories(
            @AuthenticationPrincipal StaffUserPrincipal principal
    ) {
        List<String> categories = serviceService.getCategories(principal.getTenantId());
        return ResponseEntity.ok(categories);
    }

    /**
     * Crear un nuevo servicio. Solo ADMIN.
     */
    @PostMapping("/services")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceResponse> create(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @Valid @RequestBody CreateServiceRequest request
    ) {
        ServiceEntity created = serviceService.create(principal.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    /**
     * Actualizar un servicio existente. Solo ADMIN.
     * Soporta actualizaciones parciales (solo enviar los campos a cambiar).
     */
    @PutMapping("/services/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceResponse> update(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        ServiceEntity updated = serviceService.update(principal.getTenantId(), id, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    /**
     * Desactivar un servicio (soft delete). Solo ADMIN.
     * El servicio deja de estar disponible para agendamiento y portal público.
     */
    @PatchMapping("/services/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deactivate(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id
    ) {
        serviceService.deactivate(principal.getTenantId(), id);
        return ResponseEntity.ok(Map.of("message", "Servicio desactivado"));
    }

    /**
     * Reactivar un servicio previamente desactivado. Solo ADMIN.
     */
    @PatchMapping("/services/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> activate(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id
    ) {
        serviceService.activate(principal.getTenantId(), id);
        return ResponseEntity.ok(Map.of("message", "Servicio reactivado"));
    }

    // ─── ENDPOINT PÚBLICO ─────────────────────────────────────────────

    /**
     * Catálogo público de servicios de un tenant (por alias).
     * No requiere autenticación. Sirve para landing page / portal del médico.
     * Solo devuelve servicios activos y con publicVisible=true.
     */
    @GetMapping("/public/services/{tenantAlias}")
    public ResponseEntity<?> publicCatalog(@PathVariable String tenantAlias) {
        var tenant = tenantRepository.findByAlias(tenantAlias).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Clínica no encontrada"));
        }

        List<PublicServiceResponse> services = serviceService.getPublicServices(tenant.getId())
                .stream()
                .map(this::toPublicResponse)
                .toList();

        return ResponseEntity.ok(services);
    }

    // ─── Mappers ──────────────────────────────────────────────────────

    private ServiceResponse toResponse(ServiceEntity e) {
        return new ServiceResponse(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getDurationMinutes(),
                e.getPrice(),
                e.getCurrency(),
                e.isActive(),
                e.isPublicVisible(),
                e.getCategory(),
                e.getDisplayOrder(),
                e.isRequiresPhotoConsent()
        );
    }

    private PublicServiceResponse toPublicResponse(ServiceEntity e) {
        return new PublicServiceResponse(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getDurationMinutes(),
                e.getPrice(),
                e.getCurrency(),
                e.getCategory()
        );
    }
}