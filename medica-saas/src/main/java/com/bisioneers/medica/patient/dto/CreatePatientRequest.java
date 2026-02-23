package com.bisioneers.medica.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para crear un nuevo paciente
 */
public record CreatePatientRequest(
    @NotBlank(message = "El nombre completo es requerido")
    @Size(max = 200)
    String fullName,
    
    @Email(message = "Email inválido")
    @Size(max = 160)
    String email,
    
    @Size(max = 20)
    String phone,
    
    @Size(max = 20)
    String secondaryPhone,
    
    @Size(max = 20)
    String documentType,
    
    @Size(max = 50)
    String documentNumber,
    
    LocalDate birthDate,
    
    @Pattern(regexp = "^[MFX]$", message = "Género debe ser M, F o X")
    String gender,
    
    @Size(max = 500)
    String address,
    
    String notes,
    
    boolean photoConsent,
    boolean dataConsent
) {}
