package com.bisioneers.medica.medical.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTOs para Historial Clínico y Fotos Médicas.
 */
public final class MedicalDtos {

    private MedicalDtos() {}

    // ═══════════════════════════════════════════════════════════════════
    // MEDICAL RECORD DTOs
    // ═══════════════════════════════════════════════════════════════════

    public record CreateRecordRequest(
            @NotNull UUID patientId,
            UUID appointmentId,
            LocalDateTime recordDate,

            @NotBlank @Size(max = 30)
            String recordType,

            @Size(max = 200)
            String title,

            @NotBlank
            String content,

            @Size(max = 500)
            String diagnosis,

            String treatment,
            String instructions,
            Boolean patientVisible
    ) {}

    public record UpdateRecordRequest(
            @Size(max = 30)
            String recordType,

            @Size(max = 200)
            String title,

            String content,

            @Size(max = 500)
            String diagnosis,

            String treatment,
            String instructions,
            Boolean patientVisible
    ) {}

    public record RecordResponse(
            UUID id,
            UUID patientId,
            UUID appointmentId,
            LocalDateTime recordDate,
            String recordType,
            String title,
            String content,
            String diagnosis,
            String treatment,
            String instructions,
            boolean signed,
            boolean patientVisible
    ) {}

    // ═══════════════════════════════════════════════════════════════════
    // MEDICAL PHOTO DTOs
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Metadata de la foto enviada como form fields junto al archivo.
     * El archivo se envía como multipart "file".
     */
    public record PhotoMetadata(
            @NotNull UUID patientId,
            UUID medicalRecordId,
            UUID appointmentId,

            @NotBlank @Size(max = 20)
            String photoType,

            @Size(max = 100)
            String anatomicalArea,

            @Size(max = 500)
            String notes,

            Boolean consentGiven,
            Boolean patientVisible,
            UUID pairedPhotoId
    ) {}

    public record PhotoResponse(
            UUID id,
            UUID patientId,
            UUID medicalRecordId,
            UUID appointmentId,
            String photoType,
            String storagePath,
            String originalFilename,
            String mimeType,
            Long fileSize,
            LocalDateTime capturedAt,
            String anatomicalArea,
            String notes,
            boolean consentGiven,
            boolean patientVisible,
            UUID pairedPhotoId
    ) {}

    public record PhotoPairResponse(
            PhotoResponse before,
            PhotoResponse after
    ) {}
}