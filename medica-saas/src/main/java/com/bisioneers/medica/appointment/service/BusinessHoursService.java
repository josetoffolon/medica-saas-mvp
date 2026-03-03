package com.bisioneers.medica.appointment.service;

import com.bisioneers.medica.tenant.domain.TenantEntity;
import com.bisioneers.medica.tenant.domain.TenantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**

- Valida que una cita esté dentro del horario laboral del tenant.
- 
- Los horarios se almacenan en TenantEntity.settings como JSON:
- 
- {
- “businessHours”: {
- ```
  "MONDAY":    { "open": "08:00", "close": "18:00" },
  ```
- ```
  "TUESDAY":   { "open": "08:00", "close": "18:00" },
  ```
- ```
  "WEDNESDAY": { "open": "08:00", "close": "18:00" },
  ```
- ```
  "THURSDAY":  { "open": "08:00", "close": "18:00" },
  ```
- ```
  "FRIDAY":    { "open": "08:00", "close": "17:00" },
  ```
- ```
  "SATURDAY":  { "open": "09:00", "close": "13:00" }
  ```
- }
- }
- 
- Si un día no está en el mapa, la clínica está cerrada ese día.
- Si settings es null/vacío o no tiene businessHours, se permite cualquier horario
- (para no bloquear tenants que aún no configuraron sus horarios).
 */
@Service
public class BusinessHoursService {

	private static final Logger log = LoggerFactory.getLogger(BusinessHoursService.class);

	private final TenantRepository tenantRepository;
	private final ObjectMapper objectMapper;

	public BusinessHoursService(TenantRepository tenantRepository, ObjectMapper objectMapper) {
		this.tenantRepository = tenantRepository;
		this.objectMapper = objectMapper;
	}

	/**
  - Valida que el rango [scheduledAt, scheduledAt + durationMinutes) esté
  - dentro del horario laboral del tenant.
  - 
  - @throws IllegalArgumentException si la cita cae fuera de horario
	 */
	public void validate(UUID tenantId, LocalDateTime scheduledAt, int durationMinutes) {
		Map<String, DaySchedule> hours = loadBusinessHours(tenantId);
		if (hours == null || hours.isEmpty()) {
			// Sin horarios configurados → permitir cualquier hora
			return;
		}

		DayOfWeek day = scheduledAt.getDayOfWeek();
		String dayKey = day.name(); // MONDAY, TUESDAY, etc.

		DaySchedule schedule = hours.get(dayKey);
		if (schedule == null) {
			throw new IllegalArgumentException(
					"La clínica no atiende los " + dayNameInSpanish(day) + ". "
							+ "Por favor seleccione otro día.");
		}

		LocalTime appointmentStart = scheduledAt.toLocalTime();
		LocalTime appointmentEnd = appointmentStart.plusMinutes(durationMinutes);

		LocalTime open = LocalTime.parse(schedule.open);
		LocalTime close = LocalTime.parse(schedule.close);

		if (appointmentStart.isBefore(open)) {
			throw new IllegalArgumentException(
					"La cita no puede iniciar antes de las " + schedule.open
					+ ". El horario de apertura es " + schedule.open + ".");
		}

		if (appointmentEnd.isAfter(close)) {
			throw new IllegalArgumentException(
					"La cita terminaría a las " + appointmentEnd
					+ ", después del cierre (" + schedule.close + "). "
					+ "Por favor seleccione un horario más temprano o reduzca la duración.");
		}
	}

	// ─── Internal ─────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	private Map<String, DaySchedule> loadBusinessHours(UUID tenantId) {
		try {
			TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
			if (tenant == null || tenant.getSettings() == null || tenant.getSettings().isBlank()) {
				return null;
			}
			Map<String, Object> settings = objectMapper.readValue(
					tenant.getSettings(), new TypeReference<Map<String, Object>>() {});

			Object bh = settings.get("businessHours");
			if (bh == null) return null;

			String bhJson = objectMapper.writeValueAsString(bh);
			return objectMapper.readValue(bhJson,
					new TypeReference<Map<String, DaySchedule>>() {});

		} catch (Exception e) {
			log.warn("Failed to parse business hours for tenant {}: {}", tenantId, e.getMessage());
			return null;
		}
	}

	private String dayNameInSpanish(DayOfWeek day) {
		return switch (day) {
		case MONDAY -> "lunes";
		case TUESDAY -> "martes";
		case WEDNESDAY -> "miércoles";
		case THURSDAY -> "jueves";
		case FRIDAY -> "viernes";
		case SATURDAY -> "sábados";
		case SUNDAY -> "domingos";
		};
	}

	/**
  - Estructura de horario de un día.
	 */
	public record DaySchedule(String open, String close) {}
}