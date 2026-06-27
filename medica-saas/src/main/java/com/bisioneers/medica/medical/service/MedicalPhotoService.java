package com.bisioneers.medica.medical.service;

import com.bisioneers.medica.medical.domain.MedicalPhotoEntity;
import com.bisioneers.medica.medical.domain.MedicalPhotoRepository;
import com.bisioneers.medica.medical.dto.MedicalDtos.*;
import com.bisioneers.medica.medical.storage.MediaStorageService;
import com.bisioneers.medica.patient.domain.PatientEntity;
import com.bisioneers.medica.patient.domain.PatientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para fotos médicas.
 *
 * Maneja:
 * - Upload de fotos con almacenamiento delegado a MediaStorageService
 * - Metadata de fotos (tipo, área anatómica, consentimiento)
 * - Pareamiento de fotos antes/después
 * - Descarga de fotos
 * - Generación de URLs presignadas para previsualización
 */
@Service
public class MedicalPhotoService {

	private final MedicalPhotoRepository photoRepository;
	private final MediaStorageService storageService;
	private final PatientRepository patientRepository;

	public MedicalPhotoService(MedicalPhotoRepository photoRepository,
			MediaStorageService storageService,
			PatientRepository patientRepository) {
		this.photoRepository = photoRepository;
		this.storageService = storageService;
		this.patientRepository = patientRepository;
	}

	// ─── UPLOAD ───────────────────────────────────────────────────────

	/**
	 * Sube una foto médica: guarda archivo en storage + metadata en DB.
	 */
	@Transactional
	public MedicalPhotoEntity upload(UUID tenantId, PhotoMetadata metadata, MultipartFile file) {

		// #Validar consentimiento ANTES de almacenar (no subir lo que se rechazará).
		PatientEntity patient = patientRepository.findById(metadata.patientId())
				.orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));
		if (!patient.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado al paciente");
		}
		if (!patient.isPhotoConsent()) {
			throw new IllegalStateException(
					"El paciente no ha otorgado consentimiento para fotografías médicas. " +
					"Registre el consentimiento antes de subir fotos.");
		}

		UUID photoId = UUID.randomUUID();

		String storagePath = storageService.store(tenantId, metadata.patientId(), photoId, file);

		MedicalPhotoEntity entity = new MedicalPhotoEntity();
		entity.setId(photoId);
		entity.setTenantId(tenantId);
		entity.setPatientId(metadata.patientId());
		entity.setMedicalRecordId(metadata.medicalRecordId());
		entity.setAppointmentId(metadata.appointmentId());
		entity.setPhotoType(metadata.photoType());
		entity.setStoragePath(storagePath);
		entity.setOriginalFilename(file.getOriginalFilename());
		entity.setMimeType(file.getContentType());
		entity.setFileSize(file.getSize());
		entity.setAnatomicalArea(metadata.anatomicalArea());
		entity.setNotes(metadata.notes());
		entity.setConsentGiven(
				metadata.consentGiven() != null ? metadata.consentGiven() : false);
		entity.setPatientVisible(
				metadata.patientVisible() != null ? metadata.patientVisible() : false);
		entity.setPairedPhotoId(metadata.pairedPhotoId());

		MedicalPhotoEntity saved = photoRepository.save(entity);

		if (metadata.pairedPhotoId() != null) {
			photoRepository.findById(metadata.pairedPhotoId()).ifPresent(paired -> {
				if (paired.getTenantId().equals(tenantId) && paired.getPairedPhotoId() == null) {
					paired.setPairedPhotoId(photoId);
					photoRepository.save(paired);
				}
			});
		}

		return saved;
	}

	// ─── READ ─────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public MedicalPhotoEntity getById(UUID tenantId, UUID photoId) {
		MedicalPhotoEntity photo = photoRepository.findById(photoId)
				.orElseThrow(() -> new IllegalArgumentException("Foto no encontrada"));

		if (!photo.getTenantId().equals(tenantId)) {
			throw new IllegalArgumentException("Acceso denegado");
		}

		return photo;
	}

	/**
	 * Todas las fotos de un paciente.
	 */
	@Transactional(readOnly = true)
	public List<MedicalPhotoEntity> getByPatient(UUID tenantId, UUID patientId) {
		return photoRepository.findByTenantIdAndPatientIdOrderByCapturedAtDesc(tenantId, patientId);
	}

	/**
	 * Fotos asociadas a un registro médico.
	 */
	@Transactional(readOnly = true)
	public List<MedicalPhotoEntity> getByRecord(UUID tenantId, UUID recordId) {
		return photoRepository.findByTenantIdAndMedicalRecordId(tenantId, recordId);
	}

	/**
	 * Fotos asociadas a una cita.
	 */
	@Transactional(readOnly = true)
	public List<MedicalPhotoEntity> getByAppointment(UUID tenantId, UUID appointmentId) {
		return photoRepository.findByTenantIdAndAppointmentId(tenantId, appointmentId);
	}

	/**
	 * Pares de fotos antes/después de un paciente.
	 */
	@Transactional(readOnly = true)
	public List<PhotoPairResponse> getPairedPhotos(UUID tenantId, UUID patientId) {
		List<MedicalPhotoEntity> photos =
				photoRepository.findPairedPhotos(tenantId, patientId);

		Map<UUID, List<MedicalPhotoEntity>> groups = photos.stream()
				.filter(p -> p.getPairedPhotoId() != null)
				.collect(Collectors.groupingBy(p -> {
					UUID a = p.getId();
					UUID b = p.getPairedPhotoId();
					return a.compareTo(b) < 0 ? a : b;
				}));

		List<PhotoPairResponse> pairs = new ArrayList<>();
		for (List<MedicalPhotoEntity> group : groups.values()) {
			if (group.size() == 2) {
				MedicalPhotoEntity first = group.get(0);
				MedicalPhotoEntity second = group.get(1);

				PhotoResponse before = "BEFORE".equals(first.getPhotoType())
						? toResponse(first) : toResponse(second);
				PhotoResponse after = "AFTER".equals(first.getPhotoType())
						? toResponse(first) : toResponse(second);

				pairs.add(new PhotoPairResponse(before, after));
			}
		}

		return pairs;
	}

	// ─── DOWNLOAD ─────────────────────────────────────────────────────

	/**
	 * Obtiene el InputStream de una foto para descarga/visualización.
	 */
	@Transactional(readOnly = true)
	public InputStream downloadPhoto(UUID tenantId, UUID photoId) {
		MedicalPhotoEntity photo = getById(tenantId, photoId);
		return storageService.load(photo.getStoragePath());
	}

	// ─── DELETE ───────────────────────────────────────────────────────

	/**
	 * Elimina una foto: borra archivo del storage + registro de la DB.
	 */
	@Transactional
	public void delete(UUID tenantId, UUID photoId) {
		MedicalPhotoEntity photo = getById(tenantId, photoId);

		if (photo.getPairedPhotoId() != null) {
			photoRepository.findById(photo.getPairedPhotoId()).ifPresent(paired -> {
				if (paired.getPairedPhotoId() != null && paired.getPairedPhotoId().equals(photoId)) {
					paired.setPairedPhotoId(null);
					photoRepository.save(paired);
				}
			});
		}

		storageService.delete(photo.getStoragePath());
		photoRepository.delete(photo);
	}

	// ─── Mapper ───────────────────────────────────────────────────────

	/**
	 * Convierte la entidad a DTO de respuesta.
	 *
	 * IMPORTANTE: aquí es donde se genera la URL presignada de R2 (o local)
	 * cada vez que se construye un response. La URL NO se guarda en BD —
	 * se regenera en cada request porque expira en 5 minutos.
	 */
	public PhotoResponse toResponse(MedicalPhotoEntity e) {
		String url = buildAccessUrl(e);
		return new PhotoResponse(
				e.getId(),
				e.getPatientId(),
				e.getMedicalRecordId(),
				e.getAppointmentId(),
				e.getPhotoType(),
				e.getStoragePath(),
				url,
				e.getOriginalFilename(),
				e.getMimeType(),
				e.getFileSize(),
				e.getCapturedAt(),
				e.getAnatomicalArea(),
				e.getNotes(),
				e.isConsentGiven(),
				e.isPatientVisible(),
				e.getPairedPhotoId()
				);
	}

	/**
	 * URL de acceso a la foto.
	 *
	 * - R2 (key empieza con "tenants/"): presigned URL directa (no pasa por backend).
	 * - Local (formato viejo): apunta al endpoint REAL del controller que sirve
	 *   el binario por photoId — /api/medical-photos/{id}/download.
	 *   (generateAccessUrl de LocalMediaStorageService no sirve aquí porque no
	 *    conoce el photoId; lo resolvemos donde sí lo tenemos.)
	 */
	private String buildAccessUrl(MedicalPhotoEntity e) {
		String key = e.getStoragePath();
		if (key != null && key.startsWith("tenants/")) {
			return storageService.generateAccessUrl(key); // R2 presigned
		}
		return "/api/medical-photos/" + e.getId() + "/download"; // endpoint real existente
	}
}
