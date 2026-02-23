package com.bisioneers.medica.appointment.api;

import com.bisioneers.medica.appointment.domain.AppointmentEntity;
import com.bisioneers.medica.appointment.dto.AppointmentResponse;
import com.bisioneers.medica.appointment.dto.CancelAppointmentRequest;
import com.bisioneers.medica.appointment.dto.CreateAppointmentRequest;
import com.bisioneers.medica.appointment.dto.UpdateAppointmentRequest;
import com.bisioneers.medica.appointment.service.AppointmentService;
import com.bisioneers.medica.billing.domain.TenantAware;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            Authentication auth,
            @Valid @RequestBody CreateAppointmentRequest request) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        
        AppointmentEntity entity = new AppointmentEntity();
        entity.setTenantId(tenantId);
        entity.setPatientId(request.patientId());
        entity.setServiceId(request.serviceId());
        entity.setScheduledAt(request.scheduledAt());
        entity.setDurationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 30);
        entity.setReason(request.reason());
        entity.setStaffNotes(request.staffNotes());
        entity.setPatientNotes(request.patientNotes());
        
        AppointmentEntity created = appointmentService.create(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AppointmentResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getById(
            Authentication auth,
            @PathVariable UUID id) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        AppointmentEntity appointment = appointmentService.getById(tenantId, id);
        return ResponseEntity.ok(AppointmentResponse.from(appointment));
    }

    /**
     * Obtener citas por rango de fechas
     * GET /api/appointments?startDate=2024-01-01&endDate=2024-01-31
     */
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getByDateRange(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        
        List<AppointmentEntity> appointments = appointmentService.getByDateRange(tenantId, start, end);
        return ResponseEntity.ok(appointments.stream()
            .map(AppointmentResponse::from)
            .toList());
    }

    /**
     * Obtener citas de un paciente específico
     * GET /api/appointments/patient/{patientId}
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Page<AppointmentResponse>> getByPatient(
            Authentication auth,
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        Page<AppointmentEntity> appointments = appointmentService.getByPatient(tenantId, patientId, pageable);
        return ResponseEntity.ok(appointments.map(AppointmentResponse::from));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponse> update(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        
        AppointmentEntity updates = new AppointmentEntity();
        updates.setTenantId(tenantId);
        updates.setPatientId(request.patientId());
        updates.setServiceId(request.serviceId());
        updates.setScheduledAt(request.scheduledAt());
        updates.setDurationMinutes(request.durationMinutes());
        updates.setReason(request.reason());
        updates.setStaffNotes(request.staffNotes());
        updates.setPatientNotes(request.patientNotes());
        updates.setStatus(request.status());
        
        AppointmentEntity updated = appointmentService.update(id, updates);
        return ResponseEntity.ok(AppointmentResponse.from(updated));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirm(
            Authentication auth,
            @PathVariable UUID id) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        appointmentService.confirm(tenantId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody CancelAppointmentRequest request) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        appointmentService.cancel(tenantId, id, request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(
            Authentication auth,
            @PathVariable UUID id) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        appointmentService.complete(tenantId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<Void> markNoShow(
            Authentication auth,
            @PathVariable UUID id) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        appointmentService.markNoShow(tenantId, id);
        return ResponseEntity.ok().build();
    }
}
