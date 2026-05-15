package com.bisioneers.medica.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para actualizar un paciente existente.
 *
 * Misma estructura que CreatePatientRequest — para que la edición
 * acepte exactamente los mismos campos validados.
 */
public record UpdatePatientRequest(

		@NotBlank(message = "El primer nombre es requerido")
		@Size(max = 80)
		String firstName,

		@Size(max = 80)
		String middleName,

		@NotBlank(message = "El primer apellido es requerido")
		@Size(max = 80)
		String lastName,

		@Size(max = 80)
		String secondLastName,

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

		@Pattern(regexp = "^[MF]$", message = "Género debe ser M o F")
		String gender,

		@Size(max = 80)
		String nationality,

		@Size(max = 500)
		String address,

		String medicalConditions,
		String currentMedications,
		String allergies,

		@Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Tipo de sangre inválido")
		@Size(max = 5)
		String bloodType,

		@Size(max = 200)
		String emergencyContactName,

		@Size(max = 20)
		String emergencyContactPhone,

		@Size(max = 50)
		String emergencyContactRelation,

		String notes,
		boolean photoConsent,
		boolean dataConsent
		) {}
