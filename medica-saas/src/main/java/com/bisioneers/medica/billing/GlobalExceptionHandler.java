package com.bisioneers.medica.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Manejador global de excepciones para toda la API.
 *
 * Convierte excepciones de negocio en respuestas HTTP apropiadas
 * con mensajes legibles para el frontend.
 *
 * Sin esto, Spring retorna 500 "internal_error" para cualquier
 * excepción no manejada, y el frontend no puede mostrar el
 * mensaje real (ej: "Ya existe un paciente con documento: X").
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * IllegalArgumentException → 400 Bad Request
     * Usado por todos los services para validaciones de negocio:
     * - Paciente duplicado (email/documento)
     * - Conflicto de horario en citas
     * - Servicio no encontrado
     * - Acceso denegado (tenant mismatch)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Business validation error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", ex.getMessage(),
                "status", 400,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * IllegalStateException → 409 Conflict
     * Usado para operaciones no permitidas en el estado actual:
     * - Editar registro médico firmado
     * - Desactivar último ADMIN
     * - Cambiar tenantId de entidad existente
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("State conflict error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", ex.getMessage(),
                "status", 409,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Cualquier otra excepción → 500 Internal Server Error
     * Log completo para debugging, mensaje genérico para el usuario.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Error interno del servidor",
                "message", ex.getMessage() != null ? ex.getMessage() : "Error desconocido",
                "status", 500,
                "timestamp", Instant.now().toString()
        ));
    }
}
