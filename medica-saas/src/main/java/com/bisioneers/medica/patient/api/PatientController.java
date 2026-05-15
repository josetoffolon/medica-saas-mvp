package com.bisioneers.medica.patient.api;

import com.bisioneers.medica.billing.security.StaffUserPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    // ─── CREATE ───────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<PatientResponse> create(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @Valid @RequestBody CreatePatientRequest request) {

        UUID tenantId = principal.getTenantId();

        PatientEntity entity = new PatientEntity();
        entity.setTenantId(tenantId);
        copyFromCreateRequest(entity, request);

        PatientEntity created = patientService.create(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PatientResponse.from(created));
    }

    // ─── READ ─────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> getById(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id) {

        PatientEntity patient = patientService.getById(principal.getTenantId(), id);
        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    @GetMapping
    public ResponseEntity<Page<PatientResponse>> list(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID tenantId = principal.getTenantId();
        Page<PatientEntity> patients;
        if (search != null && !search.isBlank()) {
            patients = patientService.search(tenantId, search, pageable);
        } else {
            patients = patientService.listActive(tenantId, pageable);
        }
        return ResponseEntity.ok(patients.map(PatientResponse::from));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> count(
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        long count = patientService.countActive(principal.getTenantId());
        return ResponseEntity.ok(count);
    }

    // ─── UPDATE ───────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request) {

        UUID tenantId = principal.getTenantId();

        PatientEntity updates = new PatientEntity();
        updates.setTenantId(tenantId);
        copyFromUpdateRequest(updates, request);

        PatientEntity updated = patientService.update(id, updates);
        return ResponseEntity.ok(PatientResponse.from(updated));
    }

    // ─── CONSENT ──────────────────────────────────────────────────────

    @PatchMapping("/{id}/consent")
    public ResponseEntity<Map<String, Object>> updateConsent(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> consentData) {

        boolean photoConsent = consentData.getOrDefault("photoConsent", false);
        boolean dataConsent = consentData.getOrDefault("dataConsent", false);

        patientService.updateConsent(principal.getTenantId(), id, photoConsent, dataConsent);

        return ResponseEntity.ok(Map.of(
                "message", "Consentimientos actualizados",
                "photoConsent", photoConsent,
                "dataConsent", dataConsent
        ));
    }

    // ─── DEACTIVATE / REACTIVATE ──────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id) {
        patientService.deactivate(principal.getTenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<Map<String, String>> reactivate(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id) {
        patientService.reactivate(principal.getTenantId(), id);
        return ResponseEntity.ok(Map.of("message", "Paciente reactivado"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private void copyFromCreateRequest(PatientEntity entity, CreatePatientRequest r) {
        entity.setFirstName(r.firstName());
        entity.setMiddleName(r.middleName());
        entity.setLastName(r.lastName());
        entity.setSecondLastName(r.secondLastName());
        entity.setEmail(r.email());
        entity.setPhone(r.phone());
        entity.setSecondaryPhone(r.secondaryPhone());
        entity.setDocumentType(r.documentType());
        entity.setDocumentNumber(r.documentNumber());
        entity.setBirthDate(r.birthDate());
        entity.setGender(r.gender());
        entity.setNationality(r.nationality());
        entity.setAddress(r.address());
        entity.setMedicalConditions(r.medicalConditions());
        entity.setCurrentMedications(r.currentMedications());
        entity.setAllergies(r.allergies());
        entity.setBloodType(r.bloodType());
        entity.setEmergencyContactName(r.emergencyContactName());
        entity.setEmergencyContactPhone(r.emergencyContactPhone());
        entity.setEmergencyContactRelation(r.emergencyContactRelation());
        entity.setNotes(r.notes());
        entity.setPhotoConsent(r.photoConsent());
        entity.setDataConsent(r.dataConsent());
    }

    private void copyFromUpdateRequest(PatientEntity entity, UpdatePatientRequest r) {
        entity.setFirstName(r.firstName());
        entity.setMiddleName(r.middleName());
        entity.setLastName(r.lastName());
        entity.setSecondLastName(r.secondLastName());
        entity.setEmail(r.email());
        entity.setPhone(r.phone());
        entity.setSecondaryPhone(r.secondaryPhone());
        entity.setDocumentType(r.documentType());
        entity.setDocumentNumber(r.documentNumber());
        entity.setBirthDate(r.birthDate());
        entity.setGender(r.gender());
        entity.setNationality(r.nationality());
        entity.setAddress(r.address());
        entity.setMedicalConditions(r.medicalConditions());
        entity.setCurrentMedications(r.currentMedications());
        entity.setAllergies(r.allergies());
        entity.setBloodType(r.bloodType());
        entity.setEmergencyContactName(r.emergencyContactName());
        entity.setEmergencyContactPhone(r.emergencyContactPhone());
        entity.setEmergencyContactRelation(r.emergencyContactRelation());
        entity.setNotes(r.notes());
        entity.setPhotoConsent(r.photoConsent());
        entity.setDataConsent(r.dataConsent());
    }
}
