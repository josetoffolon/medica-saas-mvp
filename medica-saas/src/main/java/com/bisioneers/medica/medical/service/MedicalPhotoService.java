package com.bisioneers.medica.medical.service;

import com.bisioneers.medica.medical.domain.MedicalPhotoEntity;
import com.bisioneers.medica.medical.domain.MedicalPhotoRepository;
import com.bisioneers.medica.medical.dto.MedicalDtos.*;
import com.bisioneers.medica.medical.storage.MediaStorageService;
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

- Servicio de negocio para fotos médicas.
- 
- Maneja:
- - Upload de fotos con almacenamiento delegado a MediaStorageService
- - Metadata de fotos (tipo, área anatómica, consentimiento)
- - Pareamiento de fotos antes/después
- - Descarga de fotos
 */
@Service
public class MedicalPhotoService {

	private final MedicalPhotoRepository photoRepository;
	private final MediaStorageService storageService;

	public MedicalPhotoService(MedicalPhotoRepository photoRepository,
			MediaStorageService storageService) {
		this.photoRepository = photoRepository;
		this.storageService = storageService;
	}

	// ─── UPLOAD ───────────────────────────────────────────────────────

	/**
  - Sube una foto médica: guarda archivo en storage + metadata en DB.
	 */
	@Transactional
	public MedicalPhotoEntity upload(UUID tenantId, PhotoMetadata metadata, MultipartFile file) {
		UUID photoId = UUID.randomUUID();

		// 1. Guardar archivo en storage
		String storagePath = storageService.store(tenantId, metadata.patientId(), photoId, file);

		// 2. Crear entidad con metadata
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

		// 3. Si tiene pairedPhotoId, actualizar la otra foto para enlazarla
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
  - Todas las fotos de un paciente.
	 */
	@Transactional(readOnly = true)
	public List<MedicalPhotoEntity> getByPatient(UUID tenantId, UUID patientId) {
		return photoRepository.findByTenantIdAndPatientIdOrderByCapturedAtDesc(tenantId, patientId);
	}

	/**
  - Fotos asociadas a un registro médico.
	 */
	@Transactional(readOnly = true)
	public List<MedicalPhotoEntity> getByRecord(UUID tenantId, UUID recordId) {
		return photoRepository.findByTenantIdAndMedicalRecordId(tenantId, recordId);
	}

	/**
  - Fotos asociadas a una cita.
	 */
	@Transactional(readOnly = true)
	public List<MedicalPhotoEntity> getByAppointment(UUID tenantId, UUID appointmentId) {
		return photoRepository.findByTenantIdAndAppointmentId(tenantId, appointmentId);
	}

	/**
  - Pares de fotos antes/después de un paciente.
	 */
	@Transactional(readOnly = true)
	public List<PhotoPairResponse> getPairedPhotos(UUID tenantId, UUID patientId) {
		List<MedicalPhotoEntity> photos =
				photoRepository.findPairedPhotos(tenantId, patientId);

		// Agrupar por pairedPhotoId para formar pares
		Map<UUID, List<MedicalPhotoEntity>> groups = photos.stream()
				.filter(p -> p.getPairedPhotoId() != null)
				.collect(Collectors.groupingBy(p -> {
					// Usar el menor UUID como key del grupo para evitar duplicados
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
  - Obtiene el InputStream de una foto para descarga/visualización.
	 */
	@Transactional(readOnly = true)
	public InputStream downloadPhoto(UUID tenantId, UUID photoId) {
		MedicalPhotoEntity photo = getById(tenantId, photoId);
		return storageService.load(photo.getStoragePath());
	}

	// ─── DELETE ───────────────────────────────────────────────────────

	/**
  - Elimina una foto: borra archivo del storage + registro de la DB.
	 */
	@Transactional
	public void delete(UUID tenantId, UUID photoId) {
		MedicalPhotoEntity photo = getById(tenantId, photoId);

		// 1. Limpiar referencia en foto pareada
		if (photo.getPairedPhotoId() != null) {
			photoRepository.findById(photo.getPairedPhotoId()).ifPresent(paired -> {
				if (paired.getPairedPhotoId() != null && paired.getPairedPhotoId().equals(photoId)) {
					paired.setPairedPhotoId(null);
					photoRepository.save(paired);
				}
			});
		}

		// 2. Eliminar archivo del storage
		storageService.delete(photo.getStoragePath());

		// 3. Eliminar registro de la DB
		photoRepository.delete(photo);
	}

	// ─── Mapper ───────────────────────────────────────────────────────

	private PhotoResponse toResponse(MedicalPhotoEntity e) {
		return new PhotoResponse(
				e.getId(), e.getPatientId(), e.getMedicalRecordId(),
				e.getAppointmentId(), e.getPhotoType(), e.getStoragePath(),
				e.getOriginalFilename(), e.getMimeType(), e.getFileSize(),
				e.getCapturedAt(), e.getAnatomicalArea(), e.getNotes(),
				e.isConsentGiven(), e.isPatientVisible(), e.getPairedPhotoId()
				);
	}
}
