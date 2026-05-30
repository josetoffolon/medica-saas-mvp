package com.bisioneers.medica.documents.domain;

/**
 * Estados del ciclo de vida de una solicitud de firma.
 *
 *  PENDING    → creada, esperando que el paciente firme
 *  SIGNED     → firmada exitosamente (terminal)
 *  EXPIRED    → caducó por tiempo sin firmar (terminal)
 *  CANCELLED  → cancelada por staff antes de firmar (terminal)
 *
 * Un PatientDocument puede tener múltiples SignatureRequests si la primera
 * expira o se cancela. Solo una puede estar PENDING a la vez.
 */
public enum SignatureRequestStatus {
    PENDING,
    SIGNED,
    EXPIRED,
    CANCELLED
}

