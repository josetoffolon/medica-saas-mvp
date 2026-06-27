package com.bisioneers.medica.billing.api;

import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.UUID;

/**
 * Return URL de Paguelo Fácil — SOLO COSMÉTICO.
 *
 * NUNCA cambia el estado del pago. La transición a PAID/DECLINED es
 * responsabilidad EXCLUSIVA del webhook validado (PagueloFacilWebhookService),
 * que verifica IP + firma + monto.
 *
 * Aquí solo devolvemos el estado AUTORITATIVO persistido en BD. Si el webhook
 * aún no llegó, el estado será PENDING y se le indica al usuario que estamos
 * confirmando.
 *
 * MOTIVO: antes este endpoint ejecutaba markAsPaid() confiando en query params
 * (Estado=Aprobada), lo que permitía a cualquiera —incluido el propio tenant,
 * que conoce su txId— activar la suscripción sin pagar.
 */
@Controller
@RequestMapping("/billing")
public class BillingReturnController {

  private static final Logger log = LoggerFactory.getLogger(BillingReturnController.class);

  private final PaymentTransactionRepository txRepo;

  public BillingReturnController(PaymentTransactionRepository txRepo) {
    this.txRepo = txRepo;
  }

  @GetMapping("/return")
  @ResponseBody
  public Map<String, String> pfReturn(@RequestParam Map<String, String> params) {
    String parm1 = params.get("PARM_1"); // txId

    if (parm1 == null || parm1.isBlank()) {
      return Map.of("status", "UNKNOWN", "message", "Falta el identificador de la transacción.");
    }

    UUID txId;
    try {
      txId = UUID.fromString(parm1);
    } catch (IllegalArgumentException e) {
      return Map.of("status", "UNKNOWN", "message", "Identificador de transacción inválido.");
    }

    PaymentTransactionEntity tx = txRepo.findById(txId).orElse(null);
    if (tx == null) {
      return Map.of("status", "UNKNOWN", "message", "Transacción no encontrada.");
    }

    // Estado AUTORITATIVO de la BD (lo setea el webhook), NO el query param.
    String status = tx.getStatus();
    String message = switch (status) {
      case "PAID"     -> "Pago confirmado. Tu suscripción está activa.";
      case "DECLINED" -> "El pago fue rechazado.";
      case "PENDING"  -> "Estamos confirmando tu pago. Esto puede tardar unos instantes.";
      default         -> "Estado del pago: " + status;
    };

    log.info("Return page viewed: tx={}, status={}", txId, status);
    return Map.of("status", status, "message", message);
  }
}