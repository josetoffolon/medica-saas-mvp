package com.bisioneers.medica.medical.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Abstracción de almacenamiento de archivos médicos.
 *
 * Implementaciones:
 *   - LocalMediaStorageService (archivos en /var/medica/uploads/)
 *   - R2MediaStorageService (Cloudflare R2 / S3-compatible)
 *   - HybridMediaStorageService (router: viejos en local, nuevos en R2)
 *
 * Selección automática vía property: medica.storage.type=local|s3|hybrid
 *
 * IMPORTANTE: el método store() retorna una storageKey opaca que se
 * guarda en BD. Para mostrarla en el frontend, llamar a generateAccessUrl()
 * que retorna una URL temporal (en R2) o una ruta del backend (local).
 */
public interface MediaStorageService {

	/**
	 * Guarda un archivo y retorna la storageKey opaca que se persiste en BD.
	 */
	String store(UUID tenantId, UUID patientId, UUID photoId, MultipartFile file);

	/** Recupera el contenido binario de un archivo guardado. */
	InputStream load(String storageKey);

	/** Elimina un archivo. Idempotente. */
	void delete(String storageKey);

	/**
	 * Genera una URL para que el cliente acceda al archivo.
	 *
	 * - Local: retorna ruta del backend (ej: /api/medical/photos/local/{key})
	 * - R2: retorna URL presignada de Cloudflare (válida 5 min)
	 */
	String generateAccessUrl(String storageKey);
}
