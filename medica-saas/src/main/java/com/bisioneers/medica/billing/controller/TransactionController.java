package com.bisioneers.medica.billing.controller;

import com.bisioneers.medica.billing.domain.PaymentEventEntity;
import com.bisioneers.medica.billing.domain.PaymentEventRepository;
import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.dto.TransactionDto;
import com.bisioneers.medica.billing.security.StaffUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * API de historial de transacciones de billing.
 *
 * Endpoints:
 *   GET /api/billing/transactions?page=0&size=20  → Listado paginado
 *   GET /api/billing/transactions/{id}            → Detalle de una
 *
 * Visible para todos los usuarios autenticados del tenant.
 * El historial de pagos es información necesaria para verificar
 * el estado de la suscripción de la clínica.
 *
 * #16: el failedReason/razón se deriva del ÚLTIMO payment_event de la
 * transacción (antes salía del payloadJson concatenado). Se resuelve aquí
 * porque TransactionDto.from es estático y no tiene acceso al repo.
 */
@RestController
@RequestMapping("/api/billing/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentEventRepository eventRepository;

    public TransactionController(PaymentTransactionRepository transactionRepository,
                                 PaymentEventRepository eventRepository) {
        this.transactionRepository = transactionRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Listar transacciones del tenant actual, paginado, más recientes primero.
     */
    @GetMapping
    public ResponseEntity<Page<TransactionDto>> getTransactions(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<PaymentTransactionEntity> entities =
                transactionRepository.findByTenantIdOrderByCreatedAtDesc(
                        principal.getTenantId(), pageable);

        Page<TransactionDto> dtos = entities.map(this::toDto);
        return ResponseEntity.ok(dtos);
    }

    /**
     * Detalle de una transacción específica.
     * Solo retorna la transacción si pertenece al tenant del usuario.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTransaction(
            @AuthenticationPrincipal StaffUserPrincipal principal,
            @PathVariable UUID id
    ) {
        return transactionRepository.findByIdAndTenantId(id, principal.getTenantId())
                .map(entity -> ResponseEntity.ok((Object) toDto(entity)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("error", "Transacción no encontrada")
                ));
    }

    // ─── Mapper ───────────────────────────────────────────────────────

    /**
     * Construye el DTO resolviendo el rawJson del último evento de pago.
     *
     * NOTA: esto introduce un query por transacción (N+1 en el listado).
     * Tolerable al volumen paginado actual (<=100 por página); si crece,
     * sustituir por un query batch del último evento por conjunto de tx.
     */
    private TransactionDto toDto(PaymentTransactionEntity tx) {
        PaymentEventEntity last =
                eventRepository.findFirstByTransactionIdOrderByCreatedAtDesc(tx.getId());
        return TransactionDto.from(tx, last != null ? last.getRawJson() : null);
    }
}