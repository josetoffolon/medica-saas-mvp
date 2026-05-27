package com.bisioneers.medica.consent.service;

import com.bisioneers.medica.consent.dto.ConsentDtos.VariableDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Whitelist de variables permitidas en plantillas de consentimiento.
 *
 * Si una plantilla usa una variable NO declarada aquí, se deja literal
 * "{{x.y}}" en el render y se reporta como unresolved (no falla).
 *
 * Para agregar una variable:
 *   1. Agregarla en VARIABLES
 *   2. Agregar el resolver en ConsentVariableRenderer.resolve()
 */
@Component
public class ConsentVariableCatalog {

	private static final List<VariableDescriptor> VARIABLES = List.of(
			// Paciente
			new VariableDescriptor("{{patient.fullName}}",          "Nombre completo del paciente",  "Paciente"),
			new VariableDescriptor("{{patient.firstName}}",         "Primer nombre",                 "Paciente"),
			new VariableDescriptor("{{patient.lastName}}",          "Primer apellido",               "Paciente"),
			new VariableDescriptor("{{patient.documentNumber}}",    "Cédula / Documento",            "Paciente"),
			new VariableDescriptor("{{patient.documentType}}",      "Tipo de documento",             "Paciente"),
			new VariableDescriptor("{{patient.email}}",             "Email del paciente",            "Paciente"),
			new VariableDescriptor("{{patient.phone}}",             "Teléfono del paciente",         "Paciente"),
			new VariableDescriptor("{{patient.birthDate}}",         "Fecha de nacimiento",           "Paciente"),
			new VariableDescriptor("{{patient.gender}}",            "Género",                        "Paciente"),
			new VariableDescriptor("{{patient.nationality}}",       "Nacionalidad",                  "Paciente"),
			new VariableDescriptor("{{patient.address}}",           "Dirección",                     "Paciente"),
			new VariableDescriptor("{{patient.bloodType}}",         "Tipo de sangre",                "Paciente"),
			new VariableDescriptor("{{patient.allergies}}",         "Alergias",                      "Paciente"),
			new VariableDescriptor("{{patient.medicalConditions}}", "Enfermedades",                  "Paciente"),
			new VariableDescriptor("{{patient.currentMedications}}","Medicamentos actuales",         "Paciente"),
			new VariableDescriptor("{{patient.emergencyName}}",     "Contacto de emergencia",        "Paciente"),
			new VariableDescriptor("{{patient.emergencyPhone}}",    "Teléfono de emergencia",        "Paciente"),
			new VariableDescriptor("{{patient.emergencyRelation}}", "Parentesco contacto",           "Paciente"),

			// Cita
			new VariableDescriptor("{{appointment.date}}",          "Fecha de la cita (DD/MM/AAAA)", "Cita"),
			new VariableDescriptor("{{appointment.time}}",          "Hora de la cita (HH:mm)",       "Cita"),

			// Servicio
			new VariableDescriptor("{{service.name}}",              "Nombre del servicio",           "Servicio"),
			new VariableDescriptor("{{service.description}}",       "Descripción del servicio",      "Servicio"),

			// Tenant / Clínica
			new VariableDescriptor("{{tenant.displayName}}",        "Nombre de la clínica",          "Clínica"),
			new VariableDescriptor("{{tenant.address}}",            "Dirección de la clínica",       "Clínica"),
			new VariableDescriptor("{{tenant.contactPhone}}",       "Teléfono de la clínica",        "Clínica"),
			new VariableDescriptor("{{tenant.contactEmail}}",       "Email de la clínica",           "Clínica"),

			// Sistema
			new VariableDescriptor("{{today}}",                     "Fecha de hoy (DD/MM/AAAA)",     "Sistema"),
			new VariableDescriptor("{{today.year}}",                "Año actual",                    "Sistema"),
			new VariableDescriptor("{{today.month}}",               "Mes actual (01-12)",            "Sistema"),
			new VariableDescriptor("{{today.day}}",                 "Día actual (01-31)",            "Sistema")
			);

	public List<VariableDescriptor> list() { return VARIABLES; }

	public boolean isKnown(String token) {
		return VARIABLES.stream().anyMatch(v -> v.token().equals(token));
	}
}
