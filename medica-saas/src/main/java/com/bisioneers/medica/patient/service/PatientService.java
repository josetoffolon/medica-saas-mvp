package com.bisioneers.medica.patient.service;

import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servicio de negocio para pacientes.
 *
 * Cambios v2 (Fase 1 - Doc 1):
 *  - update() ahora copia todos los campos nuevos:
 *    nombres en 4 partes, datos médicos, contacto de emergencia, nacionalidad
 *  - El fullName se autogenera en @PrePersist/@PreUpdate, no necesita copiarse
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional
    public PatientEntity create(PatientEntity patient) {
        // Validar email único en el tenant
        if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
            patientRepository.findByTenantIdAndEmail(patient.getTenantId(), patient.getEmail())
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "Ya existe un paciente con email: " + patient.getEmail());
                    });
        }

        // Validar documento único en el tenant
        if (patient.getDocumentNumber() != null && !patient.getDocumentNumber().isBlank()) {
            patientRepository.findByTenantIdAndDocumentNumber(
                            patient.getTenantId(), patient.getDocumentNumber())
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "Ya existe un paciente con documento: " + patient.getDocumentNumber());
                    });
        }

        return patientRepository.save(patient);
    }

    @Transactional
    public PatientEntity update(UUID id, PatientEntity updates) {
        PatientEntity existing = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

        if (!existing.getTenantId().equals(updates.getTenantId())) {
            throw new IllegalArgumentException("No se puede cambiar el tenant del paciente");
        }

        // Validar unicidad de email si cambió
        if (updates.getEmail() != null && !updates.getEmail().isBlank()
                && !updates.getEmail().equals(existing.getEmail())) {
            patientRepository.findByTenantIdAndEmail(existing.getTenantId(), updates.getEmail())
                    .ifPresent(dup -> {
                        throw new IllegalArgumentException(
                                "Ya existe un paciente con email: " + updates.getEmail());
                    });
        }

        // Validar unicidad de documento si cambió
        if (updates.getDocumentNumber() != null && !updates.getDocumentNumber().isBlank()
                && !updates.getDocumentNumber().equals(existing.getDocumentNumber())) {
            patientRepository.findByTenantIdAndDocumentNumber(
                            existing.getTenantId(), updates.getDocumentNumber())
                    .ifPresent(dup -> {
                        throw new IllegalArgumentException(
                                "Ya existe un paciente con documento: " + updates.getDocumentNumber());
                    });
        }

        // ─── Copiar campos al existente ───
        // Nombres (4 componentes) — fullName se autoregenera en @PreUpdate
        existing.setFirstName(updates.getFirstName());
        existing.setMiddleName(updates.getMiddleName());
        existing.setLastName(updates.getLastName());
        existing.setSecondLastName(updates.getSecondLastName());

        // Contacto e identificación
        existing.setEmail(updates.getEmail());
        existing.setPhone(updates.getPhone());
        existing.setSecondaryPhone(updates.getSecondaryPhone());
        existing.setDocumentType(updates.getDocumentType());
        existing.setDocumentNumber(updates.getDocumentNumber());
        existing.setBirthDate(updates.getBirthDate());
        existing.setGender(updates.getGender());
        existing.setNationality(updates.getNationality());
        existing.setAddress(updates.getAddress());

        // Datos médicos
        existing.setMedicalConditions(updates.getMedicalConditions());
        existing.setCurrentMedications(updates.getCurrentMedications());
        existing.setAllergies(updates.getAllergies());
        existing.setBloodType(updates.getBloodType());

        // Contacto de emergencia
        existing.setEmergencyContactName(updates.getEmergencyContactName());
        existing.setEmergencyContactPhone(updates.getEmergencyContactPhone());
        existing.setEmergencyContactRelation(updates.getEmergencyContactRelation());

        // Notas / consentimientos
        existing.setNotes(updates.getNotes());
        existing.setPhotoConsent(updates.isPhotoConsent());
        existing.setDataConsent(updates.isDataConsent());

        return patientRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public PatientEntity getById(UUID tenantId, UUID patientId) {
        PatientEntity patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

        if (!patient.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Acceso denegado");
        }

        return patient;
    }

    @Transactional(readOnly = true)
    public Page<PatientEntity> listActive(UUID tenantId, Pageable pageable) {
        return patientRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PatientEntity> search(UUID tenantId, String searchTerm, Pageable pageable) {
        return patientRepository.searchMultiField(tenantId, searchTerm, pageable);
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID patientId) {
        PatientEntity patient = getById(tenantId, patientId);
        patient.setActive(false);
        patientRepository.save(patient);
    }

    @Transactional
    public void reactivate(UUID tenantId, UUID patientId) {
        PatientEntity patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

        if (!patient.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Acceso denegado");
        }

        patient.setActive(true);
        patientRepository.save(patient);
    }

    @Transactional
    public void updateConsent(UUID tenantId, UUID patientId, boolean photoConsent, boolean dataConsent) {
        PatientEntity patient = getById(tenantId, patientId);
        patient.setPhotoConsent(photoConsent);
        patient.setDataConsent(dataConsent);
        patientRepository.save(patient);
    }

    @Transactional(readOnly = true)
    public long countActive(UUID tenantId) {
        return patientRepository.countByTenantIdAndActiveTrue(tenantId);
    }
}
