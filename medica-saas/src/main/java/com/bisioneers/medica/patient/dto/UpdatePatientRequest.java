package com.bisioneers.medica.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para actualizar un paciente existente
 */
public record UpdatePatientRequest(
    @NotBlank @Size(max = 200) String fullName,
    @Email @Size(max = 160) String email,
    @Size(max = 20) String phone,
    @Size(max = 20) String secondaryPhone,
    @Size(max = 20) String documentType,
    @Size(max = 50) String documentNumber,
    LocalDate birthDate,
    @Pattern(regexp = "^[MFX]$") String gender,
    @Size(max = 500) String address,
    String notes,
    boolean photoConsent,
    boolean dataConsent
) {}
