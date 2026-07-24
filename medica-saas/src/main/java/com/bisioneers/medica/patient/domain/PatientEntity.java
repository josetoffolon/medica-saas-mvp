package com.bisioneers.medica.patient.domain;

import com.bisioneers.medica.billing.tenant.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Representa un paciente en el sistema.
 *
 * Cambios v3 (Doc 1 - campos obligatorios adicionales):
 *  - Obligatorios: firstName, lastName, email, phone, documentType,
 *    documentNumber, gender, bloodType, emergencyContactName,
 *    emergencyContactPhone, emergencyContactRelation
 *  - Gender ahora se restringe a M (Masculino) o F (Femenino)
 *  - Nationality se controla desde frontend con un catálogo cerrado
 */
@Entity
@Table(name = "patient",
indexes = {
		@Index(name = "idx_patient_tenant_active", columnList = "tenant_id,active")
},
uniqueConstraints = {
		@UniqueConstraint(name = "uk_patient_tenant_email",
				columnNames = {"tenant_id", "email"}),
		@UniqueConstraint(name = "uk_patient_tenant_document",
		columnNames = {"tenant_id", "document_number"})
})
public class PatientEntity extends TenantScopedEntity {

	@Id
	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "id", columnDefinition = "BINARY(16)")
	private UUID id;

	// ─── Nombres (4 componentes) ──────────────────────────────────────

	@Column(name = "first_name", nullable = false, length = 80)
	private String firstName;

	@Column(name = "middle_name", length = 80)
	private String middleName;

	@Column(name = "last_name", nullable = false, length = 80)
	private String lastName;

	@Column(name = "second_last_name", length = 80)
	private String secondLastName;

	@Column(name = "full_name", nullable = false, length = 320)
	private String fullName;

	// ─── Contacto e identificación (obligatorios) ─────────────────────

	@Column(nullable = false, length = 160)
	private String email;

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(name = "secondary_phone", length = 20)
	private String secondaryPhone;

	@Column(name = "document_type", nullable = false, length = 20)
	private String documentType;

	@Column(name = "document_number", nullable = false, length = 50)
	private String documentNumber;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	/** Género: M (Masculino) o F (Femenino) */
	@Column(nullable = false, length = 1)
	private String gender;

	/** Nacionalidad (controlada desde frontend con catálogo cerrado) */
	@Column(length = 80)
	private String nationality;

	@Column(length = 500)
	private String address;

	// ─── Datos médicos ────────────────────────────────────────────────

	@Lob
	@Column(name = "medical_conditions", columnDefinition = "TEXT")
	private String medicalConditions;

	@Lob
	@Column(name = "current_medications", columnDefinition = "TEXT")
	private String currentMedications;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String allergies;

	/** Tipo de sangre OBLIGATORIO: A+, A-, B+, B-, AB+, AB-, O+, O- */
	@Column(name = "blood_type", nullable = false, length = 5)
	private String bloodType;

	// ─── Contacto de emergencia (OBLIGATORIO) ─────────────────────────

	@Column(name = "emergency_contact_name", nullable = false, length = 200)
	private String emergencyContactName;

	@Column(name = "emergency_contact_phone", nullable = false, length = 20)
	private String emergencyContactPhone;

	@Column(name = "emergency_contact_relation", nullable = false, length = 50)
	private String emergencyContactRelation;

	// ─── Observaciones / Estado ───────────────────────────────────────

	@Lob
	@Column(columnDefinition = "TEXT")
	private String notes;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "photo_consent", nullable = false)
	private boolean photoConsent = false;

	@Column(name = "data_consent", nullable = false)
	private boolean dataConsent = false;

	// ─── Metadatos de importación (V6) ────────────────────────────────

	@Column(name = "data_source", nullable = false, length = 20)
	private String dataSource = "MANUAL";   // MANUAL | IMPORT

	@Column(name = "profile_status", nullable = false, length = 20)
	private String profileStatus = "COMPLETE";  // COMPLETE | INCOMPLETE

	@JdbcTypeCode(SqlTypes.BINARY)
	@Column(name = "import_batch_id", columnDefinition = "BINARY(16)")
	private UUID importBatchId;

	@Column(name = "legacy_external_id", length = 64)
	private String legacyExternalId;

	// ─── Lifecycle ────────────────────────────────────────────────────

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
		regenerateFullName();
	}

	@PreUpdate
	void preUpdate() {
		regenerateFullName();
	}

	private void regenerateFullName() {
		StringBuilder sb = new StringBuilder();
		appendIfNotBlank(sb, firstName);
		appendIfNotBlank(sb, middleName);
		appendIfNotBlank(sb, lastName);
		appendIfNotBlank(sb, secondLastName);
		this.fullName = sb.toString().trim();
	}

	private static void appendIfNotBlank(StringBuilder sb, String value) {
		if (value != null && !value.isBlank()) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(value.trim());
		}
	}

	// ─── Getters / Setters ────────────────────────────────────────────

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }

	public String getFirstName() { return firstName; }
	public void setFirstName(String firstName) { this.firstName = firstName; }

	public String getMiddleName() { return middleName; }
	public void setMiddleName(String middleName) { this.middleName = middleName; }

	public String getLastName() { return lastName; }
	public void setLastName(String lastName) { this.lastName = lastName; }

	public String getSecondLastName() { return secondLastName; }
	public void setSecondLastName(String secondLastName) { this.secondLastName = secondLastName; }

	public String getFullName() { return fullName; }

	@Deprecated
	public void setFullName(String fullName) { this.fullName = fullName; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public String getSecondaryPhone() { return secondaryPhone; }
	public void setSecondaryPhone(String secondaryPhone) { this.secondaryPhone = secondaryPhone; }

	public String getDocumentType() { return documentType; }
	public void setDocumentType(String documentType) { this.documentType = documentType; }

	public String getDocumentNumber() { return documentNumber; }
	public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

	public LocalDate getBirthDate() { return birthDate; }
	public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

	public String getGender() { return gender; }
	public void setGender(String gender) { this.gender = gender; }

	public String getNationality() { return nationality; }
	public void setNationality(String nationality) { this.nationality = nationality; }

	public String getAddress() { return address; }
	public void setAddress(String address) { this.address = address; }

	public String getMedicalConditions() { return medicalConditions; }
	public void setMedicalConditions(String medicalConditions) { this.medicalConditions = medicalConditions; }

	public String getCurrentMedications() { return currentMedications; }
	public void setCurrentMedications(String currentMedications) { this.currentMedications = currentMedications; }

	public String getAllergies() { return allergies; }
	public void setAllergies(String allergies) { this.allergies = allergies; }

	public String getBloodType() { return bloodType; }
	public void setBloodType(String bloodType) { this.bloodType = bloodType; }

	public String getEmergencyContactName() { return emergencyContactName; }
	public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

	public String getEmergencyContactPhone() { return emergencyContactPhone; }
	public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }

	public String getEmergencyContactRelation() { return emergencyContactRelation; }
	public void setEmergencyContactRelation(String emergencyContactRelation) { this.emergencyContactRelation = emergencyContactRelation; }

	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }

	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }

	public boolean isPhotoConsent() { return photoConsent; }
	public void setPhotoConsent(boolean photoConsent) { this.photoConsent = photoConsent; }

	public boolean isDataConsent() { return dataConsent; }
	public void setDataConsent(boolean dataConsent) { this.dataConsent = dataConsent; }
	
	public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public String getProfileStatus() { return profileStatus; }
    public void setProfileStatus(String profileStatus) { this.profileStatus = profileStatus; }

    public UUID getImportBatchId() { return importBatchId; }
    public void setImportBatchId(UUID importBatchId) { this.importBatchId = importBatchId; }

    public String getLegacyExternalId() { return legacyExternalId; }
    public void setLegacyExternalId(String legacyExternalId) { this.legacyExternalId = legacyExternalId; }
}
