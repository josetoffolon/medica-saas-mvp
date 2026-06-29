package com.bisioneers.medica.billing;

import com.bisioneers.medica.billing.domain.PaymentEventEntity;
import com.bisioneers.medica.billing.domain.PaymentEventRepository;
import com.bisioneers.medica.billing.domain.PaymentTransactionEntity;
import com.bisioneers.medica.billing.domain.PaymentTransactionRepository;
import com.bisioneers.medica.billing.pf.PagueloFacilClient;
import com.bisioneers.medica.billing.pf.PagueloFacilLinkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class BillingService {

	private final PaymentTransactionRepository txRepo;
	private final PagueloFacilLinkClient linkClient;
	private final SubscriptionService subscriptionService;

	private final BigDecimal subscriptionAmount;
	private final String currency;
	private final String returnUrl;
	private final boolean requireAmountMatch;
	private static final Logger log = LoggerFactory.getLogger(BillingService.class);
	private final PaymentEventRepository eventRepo;

	private final PagueloFacilClient pfClient;
	private final boolean verifyWithProvider;

	public BillingService(
			PaymentTransactionRepository txRepo,
			PaymentEventRepository eventRepo,
			PagueloFacilLinkClient linkClient,
			PagueloFacilClient pfClient,
			@Value("${billing.subscription-amount}") BigDecimal subscriptionAmount,
			@Value("${billing.currency:USD}") String currency,
			@Value("${app.return-url}") String returnUrl,
			@Value("${paguelofacil.webhook.require-amount-match:false}") boolean requireAmountMatch,
			@Value("${paguelofacil.verify-with-provider:true}") boolean verifyWithProvider,
			SubscriptionService subscriptionService
			) {
		this.txRepo = txRepo;
		this.eventRepo = eventRepo;
		this.linkClient = linkClient;
		this.pfClient = pfClient;
		this.subscriptionAmount = subscriptionAmount;
		this.currency = currency;
		this.returnUrl = returnUrl;
		this.requireAmountMatch = requireAmountMatch;
		this.verifyWithProvider = verifyWithProvider;
		this.subscriptionService = subscriptionService;
	}

	/**
	 * Crea transacción PENDING y genera link de pago en PF.
	 * - Guarda payload sin pisar (append)
	 * - Si PF falla: marca ERROR + registra error en payload y relanza excepción
	 */
	@Transactional
	public CheckoutResponse startCheckout(UUID tenantId, String tenantAlias) {

		PaymentTransactionEntity tx = new PaymentTransactionEntity();
		tx.setId(UUID.randomUUID());
		tx.setTenantId(tenantId);
		tx.setProvider("PAGUELO_FACIL_LINK");
		tx.setAmount(subscriptionAmount);
		tx.setCurrency(currency);
		tx.setStatus("PENDING");
		txRepo.save(tx);

		String desc = "Suscripción mensual - " + (tenantAlias == null ? "" : tenantAlias) + " - " + tx.getId();
		String amt = subscriptionAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();

		var cmd = new PagueloFacilLinkClient.CreateLinkCommand(
				amt,
				desc,
				returnUrl,
				tx.getId().toString(), // PARM_1 = transactionId
				tenantId.toString()    // PARM_2 = tenantId
				);

		try {
			var result = linkClient.createPaymentLink(cmd);

			recordEvent(tx, "CHECKOUT", null, result.rawJson());

			txRepo.save(tx);

			return new CheckoutResponse(tx.getId(), result.checkoutUrl(), tx.getStatus());
		} catch (Exception ex) {
			tx.setStatus("ERROR");
			recordEvent(tx, "ERROR", "PF_LINK_CREATE_FAILED",
					"{\"time\":\"" + Instant.now() + "\",\"message\":\"" + safe(ex.getMessage()) + "\"}");
			txRepo.save(tx);
			throw ex;
		}
	}

	/**
	 * Webhook PF: transacción aprobada.
	 * - Idempotente (si ya está PAID no repite)
	 * - #3 Verifica que el monto reportado por PF coincida con tx.amount antes
	 *   de activar. Si no coincide → AMOUNT_MISMATCH y NO se activa.
	 *
	 * @param paidAmount monto reportado por PF (null si no vino en el payload)
	 */
	@Transactional
	public void markAsPaid(UUID txId, String providerRef, BigDecimal paidAmount, String rawBody) {

		PaymentTransactionEntity tx = txRepo.findById(txId)
				.orElseThrow(() -> new IllegalStateException("Transaccion no encontrada: " + txId));

		// Idempotencia: ya pagada
		if ("PAID".equalsIgnoreCase(tx.getStatus())) {
			if (rawBody != null && !rawBody.isBlank()) {
				recordEvent(tx, "WEBHOOK", "PAID_DUP", rawBody);
				if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
				txRepo.save(tx);
			}
			return;
		}

		// #3 Verificación de monto
		if (paidAmount != null) {
			if (tx.getAmount().compareTo(paidAmount) != 0) {
				log.error("Amount mismatch en tx {}: esperado={}, reportado={}. NO se activa.",
						txId, tx.getAmount(), paidAmount);
				tx.setStatus("AMOUNT_MISMATCH");
				recordEvent(tx, "WEBHOOK", "AMOUNT_MISMATCH",
						"{\"expected\":\"" + tx.getAmount() + "\",\"reported\":\"" + paidAmount + "\"}");
				if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
				txRepo.save(tx);
				return; // ← no activamos suscripción
			}
		} else if (requireAmountMatch) {
			// Fail-closed: PF no reportó monto y exigimos verificarlo
			log.error("PF no reportó monto para tx {} y require-amount-match=true. NO se activa.", txId);
			tx.setStatus("AMOUNT_UNVERIFIED");
			recordEvent(tx, "WEBHOOK", "AMOUNT_UNVERIFIED", "{\"error\":\"AMOUNT_UNVERIFIED\"}");
			txRepo.save(tx);
			return;
		} else {
			log.warn("PF no reportó monto para tx {} — importe no verificado (activando igual).", txId);
		}

		// DEFENSA PRINCIPAL: verificar server-to-server contra PF.
		// No confiamos en el contenido del webhook; preguntamos a PF directamente.
		if (verifyWithProvider) {
			if (providerRef == null || providerRef.isBlank()) {
				log.error("No hay codOper para verificar tx {} con PF. NO se activa.", txId);
				tx.setStatus("UNVERIFIED");
				recordEvent(tx, "WEBHOOK", "NO_OPER_TO_VERIFY", "{\"error\":\"NO_OPER_TO_VERIFY\"}");
				txRepo.save(tx);
				return;
			}
			if (!pfClient.isOperationPaid(providerRef)) {
				log.error("PF NO confirma pago de operación {} (tx {}). NO se activa.",
						providerRef, txId);
				tx.setStatus("VERIFICATION_FAILED");
				recordEvent(tx, "WEBHOOK", "VERIFICATION_FAILED",
						"{\"oper\":\"" + safe(providerRef) + "\"}");
				txRepo.save(tx);
				return; // ← webhook falsificado o pago no real: bloqueado
			}
			log.info("PF confirmó pago de operación {} (tx {})", providerRef, txId);
		}

		tx.setStatus("PAID");
		if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
		recordEvent(tx, "WEBHOOK", "PAID", rawBody);
		txRepo.save(tx);

		subscriptionService.activateFromPaidTransaction(tx.getTenantId(), tx.getId());
	}

	/**
	 * Webhook PF: transacción declinada.
	 * - Idempotente: si ya está PAID no se toca; si ya está DECLINED tampoco.
	 * - Guarda providerRef y append payload
	 */
	@Transactional
	public void markAsDeclined(UUID txId, String providerRef, String rawBody) {

		PaymentTransactionEntity tx = txRepo.findById(txId)
				.orElseThrow(() -> new IllegalStateException("Transaccion no encontrada: " + txId));

		if ("PAID".equalsIgnoreCase(tx.getStatus())) {
			// no degradamos a DECLINED
			recordEvent(tx, "WEBHOOK", "DECLINED_ON_PAID", rawBody);
			txRepo.save(tx);
			return;
		}

		if ("DECLINED".equalsIgnoreCase(tx.getStatus())) {
			recordEvent(tx, "WEBHOOK", "DECLINED_DUP", rawBody);
			if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
			txRepo.save(tx);
			return;
		}

		tx.setStatus("DECLINED");
		if (providerRef != null && !providerRef.isBlank()) tx.setProviderRef(providerRef);
		recordEvent(tx, "WEBHOOK", "DECLINED", rawBody);

		txRepo.save(tx);
	}

	public record CheckoutResponse(UUID transactionId, String redirectUrl, String status) {}

	// ---------------- helpers ----------------

	private String safe(String s) {
		if (s == null) return "";
		return s.replace("\"", "'").replace("\n", " ").replace("\r", " ");
	}

	/**
	 * Registra un evento de pago como fila en payment_event.
	 * Reemplaza el viejo appendPayload (#16): historial completo, sin
	 * crecer el campo de la transacción.
	 */
	private void recordEvent(PaymentTransactionEntity tx, String source,
			String outcome, String rawJson) {
		if (rawJson == null || rawJson.isBlank()) return;
		try {
			eventRepo.save(new PaymentEventEntity(
					tx.getTenantId(), tx.getId(), source, outcome, rawJson));
		} catch (Exception e) {
			// El registro de auditoría nunca debe romper el flujo de pago
			log.error("No se pudo registrar payment_event (tx={}, source={}): {}",
					tx.getId(), source, e.getMessage());
		}
	}
}