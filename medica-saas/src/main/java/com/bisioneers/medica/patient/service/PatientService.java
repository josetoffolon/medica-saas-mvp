package com.bisioneers.medica.patient.service;

import com.bisioneers.medica.patient.domain.PatientEntity;
import org.springframework.dao.DataIntegrityViolationException;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Servicio de negocio para pacientes.
 *
 * Cambios v2 (Fase 1 - Doc 1):
 *  - update() ahora copia todos los campos nuevos:
 *    nombres en 4 partes, datos médicos, contacto de emergencia, nacionalidad
 *  - El fullName se autogenera en @PrePersist/@PreUpdate, no necesita copiarse
 */
@Service
public class PatientService {

	private final PatientRepository patientRepository;

	public PatientService(PatientRepository patientRepository) {
		this.patientRepository = patientRepository;
	}

	@Transactional
	public PatientEntity create(PatientEntity patient) {
		// Validación previa (caso común, mensaje claro)
		if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
			patientRepository.findByTenantIdAndEmail(patient.getTenantId(), patient.getEmail())
			.ifPresent(existing -> {
				throw new IllegalArgumentException(
						"Ya existe un paciente con email: " + patient.getEmail());
			});
		}
		if (patient.getDocumentNumber() != null && !patient.getDocumentNumber().isBlank()) {
			patientRepository.findByTenantIdAndDocumentNumber(
					patient.getTenantId(), patient.getDocumentNumber())
			.ifPresent(existing -> {
				throw new IllegalArgumentException(
						"Ya existe un paciente con documento: " + patient.getDocumentNumber());
			});
		}

		// Red de seguridad ante carrera: el unique constraint de BD cierra la
		// ventana check-then-act. Traducimos la violación a un 400 amable.
		try {
			return patientRepository.saveAndFlush(patient);
		} catch (DataIntegrityViolationException e) {
			throw translateDuplicate(e, patient);
		}
	}

	@Transactional
	public PatientEntity update(UUID id, PatientEntity updates) {
		PatientEntity existing = patientRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

		if (!existing.getTenantId().equals(updates.getTenantId())) {
			throw new IllegalArgumentException("No se puede cambiar el tenant del paciente");
		}

		// Validar unicidad de email si cambió
		if (updates.getEmail() != null && !updates.getEmail().isBlank()
				&& !updates.getEmail().equals(existing.getEmail())) {
			patientRepository.findByTenantIdAndEmail(existing.getTenantId(), updates.getEmail())
			.ifPresent(dup -> {
				throw new IllegalArgumentException(
						"Ya existe un paciente con email: " + updates.getEmail());
			});
		}

		// Validar unicidad de documento si cambió
		if (updates.getDocumentNumber() != null && !updates.getDocumentNumber().isBlank()
				&& !updates.getDocumentNumber().equals(existing.getDocumentNumber())) {
			patientRepository.findByTenantIdAndDocumentNumber(
					existing.getTenantId(), updates.getDocumentNumber())
			.ifPresent(dup -> {
				throw new IllegalArgumentException(
						"Ya existe un paciente con documento: " + updates.getDocumentNumber());
			});
		}

		// ─── Copiar campos al existente ───
		// Nombres (4 componentes) — fullName se autoregenera en @PreUpdate
		existing.setFirstName(updates.getFirstName());
		existing.setMiddleName(updates.getMiddleName());
		existing.setLastName(updates.getLastName());
		existing.setSecondLastName(updates.getSecondLastName());

		// Contacto e identificación
		existing.setEmail(updates.getEmail());
		existing.setPhone(updates.getPhone());
		existing.setSecondaryPhone(updates.getSecondaryPhone());
		existing.setDocumentType(updates.getDocumentType());
		existing.setDocumentNumber(updates.getDocumentNumber());
		existing.setBirthDate(updates.getBirthDate());
		existing.setGender(updates.getGender());
		existing.setNationality(updates.getNationality());
		existing.setAddress(updates.getAddress());

		// Datos médicos
		existing.setMedicalConditions(updates.getMedicalConditions());
		existing.setCurrentMedications(updates.getCurrentMedications());
		existing.setAllergies(updates.getAllergies());
		existing.setBloodType(updates.getBloodType());

		// Contacto de emergencia
		existing.setEmergencyContactName(updates.getEmergencyContactName());
		existing.setEmergencyContactPhone(updates.getEmergencyContactPhone());
		existing.setEmergencyContactRelation(updates.getEmergencyContactRelation());

		// Notas / consentimientos
		existing.setNotes(updates.getNotes());
		existing.setPhotoConsent(updates.isPhotoConsent());
		existing.setDataConsent(updates.isDataConsent());

		// Red de seguridad ante carrera: el unique constraint de BD cierra la
		// ventana check-then-act. Traducimos la violación a un 400 amable.
		try {
			return patientRepository.saveAndFlush(existing);
		} catch (DataIntegrityViolationException e) {
			throw translateDuplicate(e, existing);
		}
	}

	@Transactional(readOnly = true)
	public PatientEntity getById(UUID tenantId, UUID patientId) {
		PatientEntity patient = patientRepository.findById(patientId)
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

		if (!patient.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado");
		}

		return patient;
	}

	@Transactional(readOnly = true)
	public Page<PatientEntity> listActive(UUID tenantId, Pageable pageable) {
		return patientRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
	}

	@Transactional(readOnly = true)
	public Page<PatientEntity> search(UUID tenantId, String searchTerm, Pageable pageable) {
		return patientRepository.searchMultiField(tenantId, searchTerm, pageable);
	}

	@Transactional
	public void deactivate(UUID tenantId, UUID patientId) {
		PatientEntity patient = getById(tenantId, patientId);
		patient.setActive(false);
		patientRepository.save(patient);
	}

	@Transactional
	public void reactivate(UUID tenantId, UUID patientId) {
		PatientEntity patient = patientRepository.findById(patientId)
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

		if (!patient.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado");
		}

		patient.setActive(true);
		patientRepository.save(patient);
	}

	@Transactional
	public void updateConsent(UUID tenantId, UUID patientId, boolean photoConsent, boolean dataConsent) {
		PatientEntity patient = getById(tenantId, patientId);
		patient.setPhotoConsent(photoConsent);
		patient.setDataConsent(dataConsent);
		patientRepository.save(patient);
	}

	@Transactional(readOnly = true)
	public long countActive(UUID tenantId) {
		return patientRepository.countByTenantIdAndActiveTrue(tenantId);
	}

	/**
	 * Traduce una violación de constraint UNIQUE a un mensaje de negocio.
	 * Necesario porque entre el findBy... y el save otra transacción concurrente
	 * pudo insertar el mismo email/documento; el constraint de BD lo bloquea y
	 * aquí lo convertimos en un 400 legible en vez de un 500.
	 */
	private IllegalArgumentException translateDuplicate(DataIntegrityViolationException e,
			PatientEntity patient) {
		String msg = e.getMostSpecificCause().getMessage();
		String lower = msg != null ? msg.toLowerCase() : "";

		if (lower.contains("uk_patient_tenant_email")) {
			return new IllegalArgumentException(
					"Ya existe un paciente con email: " + patient.getEmail());
		}
		if (lower.contains("uk_patient_tenant_document")) {
			return new IllegalArgumentException(
					"Ya existe un paciente con documento: " + patient.getDocumentNumber());
		}
		return new IllegalArgumentException("El paciente viola una restricción de unicidad.");
	}
}
