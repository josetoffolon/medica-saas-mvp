package com.bisioneers.medica.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para actualizar un paciente.
 * Misma estructura que CreatePatientRequest.
 */
public record UpdatePatientRequest(

    @NotBlank(message = "El primer nombre es obligatorio")
    @Size(max = 80)
    String firstName,

    @Size(max = 80)
    String middleName,

    @NotBlank(message = "El primer apellido es obligatorio")
    @Size(max = 80)
    String lastName,

    @Size(max = 80)
    String secondLastName,

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "Correo electrónico inválido")
    @Size(max = 160)
    String email,

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 20)
    String phone,

    @Size(max = 20)
    String secondaryPhone,

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Size(max = 20)
    String documentType,

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 50)
    String documentNumber,

    LocalDate birthDate,

    @NotBlank(message = "El género es obligatorio")
    @Pattern(regexp = "^[MF]$", message = "Género debe ser M o F")
    String gender,

    @Size(max = 80)
    String nationality,

    @Size(max = 500)
    String address,

    String medicalConditions,
    String currentMedications,
    String allergies,

    @NotBlank(message = "El tipo de sangre es obligatorio")
    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Tipo de sangre inválido")
    @Size(max = 5)
    String bloodType,

    @NotBlank(message = "El nombre del contacto de emergencia es obligatorio")
    @Size(max = 200)
    String emergencyContactName,

    @NotBlank(message = "El teléfono del contacto de emergencia es obligatorio")
    @Size(max = 20)
    String emergencyContactPhone,

    @NotBlank(message = "El parentesco del contacto de emergencia es obligatorio")
    @Size(max = 50)
    String emergencyContactRelation,

    String notes,
    boolean photoConsent,
    boolean dataConsent
) {}
