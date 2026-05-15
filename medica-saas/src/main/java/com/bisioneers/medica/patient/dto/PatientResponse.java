package com.bisioneers.medica.patient.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de respuesta con datos del paciente.
 *
 * Incluye los 4 componentes del nombre + fullName cacheado para que
 * el frontend pueda decidir cuál mostrar según la pantalla.
 */
public record PatientResponse(
    UUID id,

    // Nombres
    String firstName,
    String middleName,
    String lastName,
    String secondLastName,
    String fullName,         // computed: concatenación de los 4

    // Identificación
    String email,
    String phone,
    String secondaryPhone,
    String documentType,
    String documentNumber,
    LocalDate birthDate,
    String gender,
    String nationality,
    String address,

    // Datos médicos
    String medicalConditions,
    String currentMedications,
    String allergies,
    String bloodType,

    // Contacto de emergencia
    String emergencyContactName,
    String emergencyContactPhone,
    String emergencyContactRelation,

    // Notas / estado
    String notes,
    boolean active,
    boolean photoConsent,
    boolean dataConsent
) {
    public static PatientResponse from(com.bisioneers.medica.patient.domain.PatientEntity e) {
        return new PatientResponse(
            e.getId(),
            e.getFirstName(),
            e.getMiddleName(),
            e.getLastName(),
            e.getSecondLastName(),
            e.getFullName(),
            e.getEmail(),
            e.getPhone(),
            e.getSecondaryPhone(),
            e.getDocumentType(),
            e.getDocumentNumber(),
            e.getBirthDate(),
            e.getGender(),
            e.getNationality(),
            e.getAddress(),
            e.getMedicalConditions(),
            e.getCurrentMedications(),
            e.getAllergies(),
            e.getBloodType(),
            e.getEmergencyContactName(),
            e.getEmergencyContactPhone(),
            e.getEmergencyContactRelation(),
            e.getNotes(),
            e.isActive(),
            e.isPhotoConsent(),
            e.isDataConsent()
        );
    }
}
