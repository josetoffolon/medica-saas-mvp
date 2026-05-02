package com.bisioneers.medica.medical.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Servicio HÍBRIDO de almacenamiento.
 *
 * Permite que archivos viejos sigan en local mientras los nuevos se
 * guardan en R2. La distinción se hace por el formato de la storageKey:
 *
 *   - "tenants/..."  → R2 (formato nuevo)
 *   - cualquier otro → Local (formato viejo: {tenantId}/{patientId}/photos/...)
 *
 * Activado cuando: medica.storage.type=hybrid
 */
@Service
@Primary
@ConditionalOnProperty(name = "medica.storage.type", havingValue = "hybrid")
public class HybridMediaStorageService implements MediaStorageService {

	private static final Logger log = LoggerFactory.getLogger(HybridMediaStorageService.class);
	private static final String R2_KEY_PREFIX = "tenants/";

	private final MediaStorageService localStorage;
	private final MediaStorageService r2Storage;

	public HybridMediaStorageService(
			@Qualifier("localMediaStorageService") MediaStorageService localStorage,
			@Qualifier("r2MediaStorageService") MediaStorageService r2Storage) {
		this.localStorage = localStorage;
		this.r2Storage = r2Storage;
		log.info("HybridMediaStorageService active: NEW files → R2, OLD files → Local");
	}

	/**
	 * Los nuevos archivos siempre van a R2.
	 * Los archivos viejos en disco no se mueven.
	 */
	@Override
	public String store(UUID tenantId, UUID patientId, UUID photoId, MultipartFile file) {
		return r2Storage.store(tenantId, patientId, photoId, file);
	}

	@Override
	public InputStream load(String storageKey) {
		return route(storageKey).load(storageKey);
	}

	@Override
	public void delete(String storageKey) {
		route(storageKey).delete(storageKey);
	}

	@Override
	public String generateAccessUrl(String storageKey) {
		return route(storageKey).generateAccessUrl(storageKey);
	}

	/**
	 * Decide qué storage usar según el formato de la key.
	 * Las keys de R2 empiezan con "tenants/" — todo lo demás se asume local.
	 */
	private MediaStorageService route(String storageKey) {
		if (storageKey != null && storageKey.startsWith(R2_KEY_PREFIX)) {
			return r2Storage;
		}
		return localStorage;
	}
}
