package com.bisioneers.medica.audit.service;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Aspecto AOP que intercepta automáticamente las operaciones de los services
 * y registra eventos de auditoría sin modificar el código existente.
 *
 * Intercepta métodos en todos los services del paquete medica que:
 * - Empiecen con create/save → acción CREATE
 * - Empiecen con update → acción UPDATE
 * - Empiecen con delete/deactivate → acción DELETE/DEACTIVATE
 * - Empiecen con sign → acción SIGN
 * - Empiecen con unsign → acción UNSIGN
 * - Empiecen con activate/reactivate → acción ACTIVATE
 * - Empiecen con confirm/complete/cancel → acción correspondiente
 */
@Aspect
@Component
public class AuditAspect {

	private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

	private final AuditLogService auditLogService;

	public AuditAspect(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	// ─── Patient operations ───────────────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.patient.service.PatientService.create*(..))",
			returning = "result"
			)
	public void auditPatientCreate(JoinPoint jp, Object result) {
		recordFromResult("CREATE", "PATIENT", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.patient.service.PatientService.update*(..))",
			returning = "result"
			)
	public void auditPatientUpdate(JoinPoint jp, Object result) {
		recordFromResult("UPDATE", "PATIENT", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.patient.service.PatientService.deactivate*(..))"
			)
	public void auditPatientDeactivate(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("DEACTIVATE", "PATIENT", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.patient.service.PatientService.reactivate*(..))"
			)
	public void auditPatientReactivate(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("ACTIVATE", "PATIENT", id, null);
	}

	// ─── Appointment operations ───────────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.appointment.service.AppointmentService.create*(..))",
			returning = "result"
			)
	public void auditAppointmentCreate(JoinPoint jp, Object result) {
		recordFromResult("CREATE", "APPOINTMENT", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.appointment.service.AppointmentService.confirm*(..))"
			)
	public void auditAppointmentConfirm(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("CONFIRM", "APPOINTMENT", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.appointment.service.AppointmentService.complete*(..))"
			)
	public void auditAppointmentComplete(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("COMPLETE", "APPOINTMENT", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.appointment.service.AppointmentService.cancel*(..))"
			)
	public void auditAppointmentCancel(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("CANCEL", "APPOINTMENT", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.appointment.service.AppointmentService.markNoShow*(..))"
			)
	public void auditAppointmentNoShow(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("NO_SHOW", "APPOINTMENT", id, null);
	}

	// ─── Medical Record operations ────────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.medical.service.MedicalRecordService.create*(..))",
			returning = "result"
			)
	public void auditRecordCreate(JoinPoint jp, Object result) {
		recordFromResult("CREATE", "MEDICAL_RECORD", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.medical.service.MedicalRecordService.sign*(..))"
			)
	public void auditRecordSign(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("SIGN", "MEDICAL_RECORD", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.medical.service.MedicalRecordService.unsign*(..))"
			)
	public void auditRecordUnsign(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("UNSIGN", "MEDICAL_RECORD", id, null);
	}

	// ─── Medical Photo operations ─────────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.medical.service.MedicalPhotoService.upload*(..))",
			returning = "result"
			)
	public void auditPhotoUpload(JoinPoint jp, Object result) {
		recordFromResult("UPLOAD", "MEDICAL_PHOTO", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.medical.service.MedicalPhotoService.delete*(..))"
			)
	public void auditPhotoDelete(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("DELETE", "MEDICAL_PHOTO", id, null);
	}

	// ─── Service Catalog operations ───────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.service.service.ServiceService.create*(..))",
			returning = "result"
			)
	public void auditServiceCreate(JoinPoint jp, Object result) {
		recordFromResult("CREATE", "SERVICE", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.service.service.ServiceService.update*(..))",
			returning = "result"
			)
	public void auditServiceUpdate(JoinPoint jp, Object result) {
		recordFromResult("UPDATE", "SERVICE", result);
	}

	// ─── Staff operations ─────────────────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.tenant.service.StaffService.create*(..))",
			returning = "result"
			)
	public void auditStaffCreate(JoinPoint jp, Object result) {
		recordFromResult("CREATE", "STAFF", result);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.tenant.service.StaffService.deactivate*(..))"
			)
	public void auditStaffDeactivate(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("DEACTIVATE", "STAFF", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.tenant.service.StaffService.reactivate*(..))"
			)
	public void auditStaffReactivate(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("ACTIVATE", "STAFF", id, null);
	}

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.tenant.service.StaffService.resetPassword*(..))"
			)
	public void auditStaffResetPassword(JoinPoint jp) {
		UUID id = extractFirstUuidArg(jp);
		auditLogService.record("RESET_PASSWORD", "STAFF", id, null);
	}

	// ─── Tenant operations ────────────────────────────

	@AfterReturning(
			pointcut = "execution(* com.bisioneers.medica.tenant.service.TenantService.update*(..))",
			returning = "result"
			)
	public void auditTenantUpdate(JoinPoint jp, Object result) {
		recordFromResult("UPDATE", "TENANT", result);
	}

	// ─── Helpers ──────────────────────────────────────

	/**
	 * Extracts the ID and a display name from the returned entity
	 * using reflection (getId(), getFullName()/getName()/getEmail()).
	 */
	private void recordFromResult(String action, String entityType, Object result) {
		if (result == null) return;
		try {
			UUID id = invokeMethod(result, "getId", UUID.class);
			String name = extractEntityName(result);
			auditLogService.record(action, entityType, id, name);
		} catch (Exception e) {
			log.debug("Could not extract audit info from {}: {}", entityType, e.getMessage());
			auditLogService.record(action, entityType, null, null);
		}
	}

	private String extractEntityName(Object obj) {
		// Try different name getters in order of preference
		String[] methods = {"getFullName", "getName", "getTitle", "getEmail", "getOriginalFilename"};
		for (String methodName : methods) {
			String value = invokeMethod(obj, methodName, String.class);
			if (value != null) return value;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T> T invokeMethod(Object obj, String methodName, Class<T> returnType) {
		try {
			Method method = obj.getClass().getMethod(methodName);
			Object value = method.invoke(obj);
			if (returnType.isInstance(value)) {
				return (T) value;
			}
		} catch (Exception ignored) {}
		return null;
	}

	private UUID extractFirstUuidArg(JoinPoint jp) {
		for (Object arg : jp.getArgs()) {
			if (arg instanceof UUID uuid) return uuid;
		}
		return null;
	}
}
