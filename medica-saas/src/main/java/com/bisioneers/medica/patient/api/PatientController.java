package com.bisioneers.medica.patient.api;

import com.bisioneers.medica.billing.domain.TenantAware;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.dto.CreatePatientRequest;
import com.bisioneers.medica.patient.dto.PatientResponse;
import com.bisioneers.medica.patient.dto.UpdatePatientRequest;
import com.bisioneers.medica.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    public ResponseEntity<PatientResponse> create(
            Authentication auth,
            @Valid @RequestBody CreatePatientRequest request) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        
        PatientEntity entity = new PatientEntity();
        entity.setTenantId(tenantId);
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setSecondaryPhone(request.secondaryPhone());
        entity.setDocumentType(request.documentType());
        entity.setDocumentNumber(request.documentNumber());
        entity.setBirthDate(request.birthDate());
        entity.setGender(request.gender());
        entity.setAddress(request.address());
        entity.setNotes(request.notes());
        entity.setPhotoConsent(request.photoConsent());
        entity.setDataConsent(request.dataConsent());
        
        PatientEntity created = patientService.create(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PatientResponse.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getById(
            Authentication auth,
            @PathVariable UUID id) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        PatientEntity patient = patientService.getById(tenantId, id);
        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    @GetMapping
    public ResponseEntity<Page<PatientResponse>> list(
            Authentication auth,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        
        Page<PatientEntity> patients;
        if (search != null && !search.isBlank()) {
            patients = patientService.search(tenantId, search, pageable);
        } else {
            patients = patientService.listActive(tenantId, pageable);
        }
        
        return ResponseEntity.ok(patients.map(PatientResponse::from));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        
        PatientEntity updates = new PatientEntity();
        updates.setTenantId(tenantId);
        updates.setFullName(request.fullName());
        updates.setEmail(request.email());
        updates.setPhone(request.phone());
        updates.setSecondaryPhone(request.secondaryPhone());
        updates.setDocumentType(request.documentType());
        updates.setDocumentNumber(request.documentNumber());
        updates.setBirthDate(request.birthDate());
        updates.setGender(request.gender());
        updates.setAddress(request.address());
        updates.setNotes(request.notes());
        updates.setPhotoConsent(request.photoConsent());
        updates.setDataConsent(request.dataConsent());
        
        PatientEntity updated = patientService.update(id, updates);
        return ResponseEntity.ok(PatientResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            Authentication auth,
            @PathVariable UUID id) {
        
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        patientService.deactivate(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    public ResponseEntity<Long> count(Authentication auth) {
        UUID tenantId = ((TenantAware) auth.getPrincipal()).getTenantId();
        long count = patientService.countActive(tenantId);
        return ResponseEntity.ok(count);
    }
}
