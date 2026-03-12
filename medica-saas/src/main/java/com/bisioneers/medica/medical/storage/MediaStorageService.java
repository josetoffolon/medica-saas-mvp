package com.bisioneers.medica.medical.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**

- Abstracción de almacenamiento de archivos médicos.
- 
- MVP: LocalMediaStorageService (filesystem local)
- Producción: reemplazar con S3MediaStorageService (AWS S3 / MinIO)
- 
- Convención de paths:
- {tenantId}/{patientId}/photos/{photoId}_{originalFilename}
  */
  public interface MediaStorageService {

/**
 * Almacena un archivo y retorna el path relativo donde quedó guardado.
 *
 * @param tenantId  ID del tenant (para aislar archivos por tenant)
 * @param patientId ID del paciente
 * @param photoId   ID de la foto (para nombre único)
 * @param file      archivo subido
 * @return path relativo del archivo almacenado
 */
String store(UUID tenantId, UUID patientId, UUID photoId, MultipartFile file);

/**
 * Obtiene un InputStream del archivo almacenado.
 *
 * @param storagePath path relativo retornado por store()
 * @return InputStream del archivo
 */
InputStream load(String storagePath);

/**
 * Elimina un archivo del almacenamiento.
 *
 * @param storagePath path relativo retornado por store()
 */
void delete(String storagePath);

/**
 * Verifica si un archivo existe.
 *
 * @param storagePath path relativo
 * @return true si el archivo existe
 */
boolean exists(String storagePath);

}