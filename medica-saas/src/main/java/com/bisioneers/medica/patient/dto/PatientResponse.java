package com.bisioneers.medica.patient.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de respuesta con datos del paciente
 */
public record PatientResponse(
    UUID id,
    String fullName,
    String email,
    String phone,
    String secondaryPhone,
    String documentType,
    String documentNumber,
    LocalDate birthDate,
    String gender,
    String address,
    String notes,
    boolean active,
    boolean photoConsent,
    boolean dataConsent
) {
    public static PatientResponse from(com.bisioneers.medica.patient.domain.PatientEntity entity) {
        return new PatientResponse(
            entity.getId(),
            entity.getFullName(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getSecondaryPhone(),
            entity.getDocumentType(),
            entity.getDocumentNumber(),
            entity.getBirthDate(),
            entity.getGender(),
            entity.getAddress(),
            entity.getNotes(),
            entity.isActive(),
            entity.isPhotoConsent(),
            entity.isDataConsent()
        );
    }
}

