package com.bisioneers.medica.billing.api;

import com.bisioneers.medica.billing.BillingService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.UUID;

@Controller
@RequestMapping("/billing")
public class BillingReturnController {

  private final BillingService billingService;

  public BillingReturnController(BillingService billingService) {
    this.billingService = billingService;
  }

  @GetMapping("/return")
  @ResponseBody
  public String pfReturn(@RequestParam java.util.Map<String, String> params) {

    String parm1 = params.get("PARM_1"); // txId
    String oper  = params.get("Oper");   // LK-...
    String estado = params.get("Estado"); // Aprobada / Denegada

    if (parm1 == null) return "Missing PARM_1";

    UUID txId = UUID.fromString(parm1);

    String raw = params.toString(); // MVP (luego JSON)

    if ("Aprobada".equalsIgnoreCase(estado)) {
      billingService.markAsPaid(txId, oper, raw);
      return "Pago aprobado. Suscripción activada.";
    } else {
      billingService.markAsDeclined(txId, oper, raw);
      return "Pago no aprobado.";
    }
  }
}

