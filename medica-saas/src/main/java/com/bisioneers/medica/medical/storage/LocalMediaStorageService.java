package com.bisioneers.medica.medical.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación local (filesystem) del almacenamiento de fotos médicas.
 *
 * Estructura en disco:
 *   {basePath}/{tenantId}/{patientId}/photos/{photoId}_{filename}
 */
@Service("localMediaStorageService")
@ConditionalOnExpression(
		"'${medica.storage.type:local}' == 'local' || '${medica.storage.type:local}' == 'hybrid'"
		)
public class LocalMediaStorageService implements MediaStorageService {

	private static final Logger log = LoggerFactory.getLogger(LocalMediaStorageService.class);

	private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp"
			);

	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

	@Value("${medica.storage.base-path:./uploads}")
	private String basePath;

	private Path rootPath;

	@PostConstruct
	void init() {
		rootPath = Paths.get(basePath).toAbsolutePath().normalize();
		try {
			Files.createDirectories(rootPath);
			log.info("Media storage initialized at: {}", rootPath);
		} catch (IOException e) {
			throw new RuntimeException("Cannot create storage directory: " + rootPath, e);
		}
	}

	@Override
	public String store(UUID tenantId, UUID patientId, UUID photoId, MultipartFile file) {
		validateFile(file);

		String safeFilename = sanitizeFilename(file.getOriginalFilename());
		String relativePath = String.format("%s/%s/photos/%s_%s",
				tenantId, patientId, photoId, safeFilename);

		Path targetPath = rootPath.resolve(relativePath).normalize();
		if (!targetPath.startsWith(rootPath)) {
			throw new IllegalArgumentException("Path traversal detected");
		}

		try {
			Files.createDirectories(targetPath.getParent());
			Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
			log.debug("Stored photo: {}", relativePath);
		} catch (IOException e) {
			log.error("Failed to store file: {}", e.getMessage());
			throw new RuntimeException("Failed to store file: " + relativePath, e);
		}

		return relativePath;
	}

	@Override
	public InputStream load(String storageKey) {
		Path filePath = rootPath.resolve(storageKey).normalize();
		if (!filePath.startsWith(rootPath)) {
			throw new IllegalArgumentException("Path traversal detected");
		}

		try {
			return Files.newInputStream(filePath);
		} catch (IOException e) {
			throw new RuntimeException("File not found: " + storageKey, e);
		}
	}

	@Override
	public void delete(String storageKey) {
		Path filePath = rootPath.resolve(storageKey).normalize();
		if (!filePath.startsWith(rootPath)) {
			throw new IllegalArgumentException("Path traversal detected");
		}

		try {
			boolean deleted = Files.deleteIfExists(filePath);
			if (deleted) {
				log.debug("Deleted photo: {}", storageKey);
			}
		} catch (IOException e) {
			log.warn("Failed to delete file: {}", storageKey, e);
		}
	}

	@Override
	public String generateAccessUrl(String storageKey) {
		// Para archivos locales: ruta del endpoint del backend que los sirve.
		// Tu MedicalPhotoController ya sirve via downloadPhoto(photoId).
		// Aquí simplemente referenciamos el endpoint existente (necesitarás el photoId,
		// no la storageKey, así que lo más limpio es manejarlo en el controller).
		return "/api/medical/photos/local/" + storageKey;
	}

	public boolean exists(String storageKey) {
		Path filePath = rootPath.resolve(storageKey).normalize();
		return filePath.startsWith(rootPath) && Files.exists(filePath);
	}

	// ─── Validación ───────────────────────────────────────────────────

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("El archivo está vacío");
		}

		if (file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException(
					"El archivo excede el tamaño máximo de 10MB");
		}

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
			throw new IllegalArgumentException(
					"Tipo de archivo no permitido. Solo se aceptan: JPEG, PNG, WebP");
		}
	}

	private String sanitizeFilename(String filename) {
		if (filename == null || filename.isBlank()) {
			return "photo.jpg";
		}
		return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
