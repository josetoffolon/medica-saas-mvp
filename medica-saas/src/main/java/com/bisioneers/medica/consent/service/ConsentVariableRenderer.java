package com.bisioneers.medica.consent.service;

import com.bisioneers.medica.consent.dto.ConsentDtos.RenderedConsentResponse;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderiza HTML de consentimiento sustituyendo variables {{x.y}}.
 *
 * Reglas de seguridad:
 *  - El HTML que entra YA está sanitizado por Jsoup (defensa en capas)
 *  - Los VALORES de las variables se ESCAPAN como entidades HTML antes de
 *    insertarse (datos del paciente con "<", ">", "&" no rompen el HTML
 *    ni inyectan markup)
 *  - Variables no reconocidas se dejan literal y se reportan
 */
@Component
public class ConsentVariableRenderer {

	private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
	private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Panama");

	/**
	 * Contexto opcional para render. Cualquier campo puede venir null y la
	 * variable correspondiente quedará como unresolved.
	 */
	public record RenderContext(
			ZoneId tenantZone,
			// Paciente
			String patientFullName,
			String patientFirstName,
			String patientLastName,
			String patientDocumentType,
			String patientDocumentNumber,
			String patientEmail,
			String patientPhone,
			LocalDate patientBirthDate,
			String patientGender,
			String patientNationality,
			String patientAddress,
			String patientBloodType,
			String patientAllergies,
			String patientMedicalConditions,
			String patientCurrentMedications,
			String patientEmergencyName,
			String patientEmergencyPhone,
			String patientEmergencyRelation,
			// Cita
			LocalDateTime appointmentAt,
			// Servicio
			String serviceName,
			String serviceDescription,
			// Tenant
			String tenantDisplayName,
			String tenantAddress,
			String tenantContactPhone,
			String tenantContactEmail
			) {}

	public RenderedConsentResponse render(
			UUID versionId,
			int versionNumber,
			String title,
			String sanitizedHtml,
			RenderContext ctx
			) {
		Set<String> unresolved = new LinkedHashSet<>();
		Matcher m = VAR_PATTERN.matcher(sanitizedHtml);
		StringBuilder out = new StringBuilder();

		while (m.find()) {
			String token = "{{" + m.group(1) + "}}";
			String value = resolve(token, ctx);
			if (value == null) {
				unresolved.add(token);
				m.appendReplacement(out, Matcher.quoteReplacement(token));
			} else {
				m.appendReplacement(out, Matcher.quoteReplacement(Entities.escape(value)));
			}
		}
		m.appendTail(out);

		return new RenderedConsentResponse(
				versionId, versionNumber, title, out.toString(),
				new ArrayList<>(unresolved)
				);
	}

	private String resolve(String token, RenderContext ctx) {
		if (ctx == null) return null;
		ZoneId zone = ctx.tenantZone() != null ? ctx.tenantZone() : DEFAULT_ZONE;
		LocalDate today = LocalDate.now(zone);

		return switch (token) {
		case "{{patient.fullName}}"          -> ctx.patientFullName();
		case "{{patient.firstName}}"         -> ctx.patientFirstName();
		case "{{patient.lastName}}"          -> ctx.patientLastName();
		case "{{patient.documentType}}"      -> ctx.patientDocumentType();
		case "{{patient.documentNumber}}"    -> ctx.patientDocumentNumber();
		case "{{patient.email}}"             -> ctx.patientEmail();
		case "{{patient.phone}}"             -> ctx.patientPhone();
		case "{{patient.birthDate}}"         -> ctx.patientBirthDate() == null
				? null : ctx.patientBirthDate().format(DATE_FMT);
		case "{{patient.gender}}"            -> ctx.patientGender();
		case "{{patient.nationality}}"       -> ctx.patientNationality();
		case "{{patient.address}}"           -> ctx.patientAddress();
		case "{{patient.bloodType}}"         -> ctx.patientBloodType();
		case "{{patient.allergies}}"         -> ctx.patientAllergies();
		case "{{patient.medicalConditions}}" -> ctx.patientMedicalConditions();
		case "{{patient.currentMedications}}"-> ctx.patientCurrentMedications();
		case "{{patient.emergencyName}}"     -> ctx.patientEmergencyName();
		case "{{patient.emergencyPhone}}"    -> ctx.patientEmergencyPhone();
		case "{{patient.emergencyRelation}}" -> ctx.patientEmergencyRelation();

		case "{{appointment.date}}"          -> ctx.appointmentAt() == null
				? null : ctx.appointmentAt().format(DATE_FMT);
		case "{{appointment.time}}"          -> ctx.appointmentAt() == null
				? null : ctx.appointmentAt().format(TIME_FMT);

		case "{{service.name}}"              -> ctx.serviceName();
		case "{{service.description}}"       -> ctx.serviceDescription();

		case "{{tenant.displayName}}"        -> ctx.tenantDisplayName();
		case "{{tenant.address}}"            -> ctx.tenantAddress();
		case "{{tenant.contactPhone}}"       -> ctx.tenantContactPhone();
		case "{{tenant.contactEmail}}"       -> ctx.tenantContactEmail();

		case "{{today}}"                     -> today.format(DATE_FMT);
		case "{{today.year}}"                -> String.valueOf(today.getYear());
		case "{{today.month}}"               -> String.format("%02d", today.getMonthValue());
		case "{{today.day}}"                 -> String.format("%02d", today.getDayOfMonth());

		default -> null;
		};
	}
}
