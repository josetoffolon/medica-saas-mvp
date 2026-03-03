package com.bisioneers.medica.patient.service;

import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**

- Servicio de negocio para pacientes.
- 
- CAMBIOS vs versión anterior:
- - search() ahora usa searchMultiField (nombre, email, teléfono, documento)
- - Agregado reactivate() para restaurar pacientes desactivados
- - updateConsent() ya existía (sin cambios)
 */
@Service
public class PatientService {

	private final PatientRepository patientRepository;

	public PatientService(PatientRepository patientRepository) {
		this.patientRepository = patientRepository;
	}

	@Transactional
	public PatientEntity create(PatientEntity patient) {
		// Validar que no exista ya con ese email en el tenant
		if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
			patientRepository.findByTenantIdAndEmail(patient.getTenantId(), patient.getEmail())
			.ifPresent(existing -> {
				throw new IllegalArgumentException(
						"Ya existe un paciente con email: " + patient.getEmail());
			});
		}

		// Validar documento único si está presente
		if (patient.getDocumentNumber() != null && !patient.getDocumentNumber().isBlank()) {
			patientRepository.findByTenantIdAndDocumentNumber(
					patient.getTenantId(), patient.getDocumentNumber())
			.ifPresent(existing -> {
				throw new IllegalArgumentException(
						"Ya existe un paciente con documento: " + patient.getDocumentNumber());
			});
		}

		return patientRepository.save(patient);

	}

	@Transactional
	public PatientEntity update(UUID id, PatientEntity updates) {
		PatientEntity existing = patientRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

		// Validar tenant (seguridad)
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

		// Actualizar campos
		existing.setFullName(updates.getFullName());
		existing.setEmail(updates.getEmail());
		existing.setPhone(updates.getPhone());
		existing.setSecondaryPhone(updates.getSecondaryPhone());
		existing.setDocumentType(updates.getDocumentType());
		existing.setDocumentNumber(updates.getDocumentNumber());
		existing.setBirthDate(updates.getBirthDate());
		existing.setGender(updates.getGender());
		existing.setAddress(updates.getAddress());
		existing.setNotes(updates.getNotes());
		existing.setPhotoConsent(updates.isPhotoConsent());
		existing.setDataConsent(updates.isDataConsent());

		return patientRepository.save(existing);

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

	/**
  - Búsqueda multi-campo: nombre, email, teléfono, documento.
  - 
  - CAMBIO: Antes solo buscaba por nombre (searchByName).
  - Ahora busca en 4 campos simultáneamente.
	 */
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

	/**
  - Reactivar un paciente previamente desactivado.
	 */
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

	/**
  - Actualizar consentimientos del paciente.
  - Este método ya existía pero no estaba expuesto en el controller.
	 */
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
}