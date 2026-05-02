package com.bisioneers.medica.medical.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento de fotos médicas en Cloudflare R2 (compatible S3).
 *
 * - Bucket privado, sin acceso público
 * - URLs presignadas con TTL de 5 minutos
 * - Estructura de keys: tenants/{tenantId}/patients/{patientId}/photos/{photoId}_{filename}
 *
 * Activado solo si: medica.storage.type=s3 o hybrid (este se usa internamente)
 */
@Service("r2MediaStorageService")
@ConditionalOnExpression(
		"'${medica.storage.type:local}' == 's3' || '${medica.storage.type:local}' == 'hybrid'"
		)
public class R2MediaStorageService implements MediaStorageService {

	private static final Logger log = LoggerFactory.getLogger(R2MediaStorageService.class);
	private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

	private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp"
			);
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;

	public R2MediaStorageService(S3Client s3Client,
			S3Presigner s3Presigner,
			@Value("${medica.storage.s3.bucket}") String bucket) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.bucket = bucket;
		log.info("R2MediaStorageService initialized with bucket: {}", bucket);
	}

	@Override
	public String store(UUID tenantId, UUID patientId, UUID photoId, MultipartFile file) {
		validateFile(file);

		String safeFilename = sanitizeFilename(file.getOriginalFilename());
		String key = String.format("tenants/%s/patients/%s/photos/%s_%s",
				tenantId, patientId, photoId, safeFilename);

		try (InputStream is = file.getInputStream()) {
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.contentType(file.getContentType())
					.contentLength(file.getSize())
					.build();

			s3Client.putObject(request, RequestBody.fromInputStream(is, file.getSize()));
			log.info("Stored {} bytes at R2 key: {}", file.getSize(), key);
			return key;
		} catch (IOException e) {
			throw new RuntimeException("Failed to read uploaded file", e);
		} catch (S3Exception e) {
			throw new RuntimeException("Failed to upload to R2: " + e.getMessage(), e);
		}
	}

	@Override
	public InputStream load(String storageKey) {
		try {
			GetObjectRequest request = GetObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build();
			return s3Client.getObject(request);
		} catch (NoSuchKeyException e) {
			throw new RuntimeException("File not found in R2: " + storageKey, e);
		} catch (S3Exception e) {
			throw new RuntimeException("Failed to download from R2: " + e.getMessage(), e);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build();
			s3Client.deleteObject(request);
			log.info("Deleted R2 key: {}", storageKey);
		} catch (S3Exception e) {
			log.warn("Failed to delete R2 key {}: {}", storageKey, e.getMessage());
		}
	}

	@Override
	public String generateAccessUrl(String storageKey) {
		try {
			GetObjectRequest getRequest = GetObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build();

			GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
					.signatureDuration(PRESIGN_TTL)
					.getObjectRequest(getRequest)
					.build();

			URL presignedUrl = s3Presigner.presignGetObject(presignRequest).url();
			return presignedUrl.toString();
		} catch (Exception e) {
			log.error("Failed to presign URL for key {}: {}", storageKey, e.getMessage());
			throw new RuntimeException("No se pudo generar URL de acceso", e);
		}
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
		if (filename == null || filename.isBlank()) return "photo.jpg";
		return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
