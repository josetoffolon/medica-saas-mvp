package com.bisioneers.medica.billing.pf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bisioneers.medica.billing.pf.dto.CreateActivityRequest;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class PagueloFacilClient {

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final Duration timeout;
	private static final Logger log = LoggerFactory.getLogger(PagueloFacilClient.class);

	private static final String ACTIVITIES_PATH = "/PFManagementServices/api/v1/Activities/";

	public PagueloFacilClient(
			@Value("${paguelofacil.base-url}") String baseUrl,
			@Value("${paguelofacil.access-token}") String accessToken,
			@Value("${paguelofacil.timeout-ms:30000}") long timeoutMs,
			ObjectMapper objectMapper
			) {
		this.objectMapper = objectMapper;
		this.timeout = Duration.ofMillis(timeoutMs);

		// HttpClient con timeouts a nivel de socket. Sin esto, el connector
		// por defecto no tiene connect/response timeout y el .timeout() de
		// Reactor cancela el canal a mitad del handshake TLS -> ClosedChannelException.
		HttpClient httpClient = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
				.responseTimeout(this.timeout)
				.doOnConnected(conn -> conn.addHandlerLast(
						new ReadTimeoutHandler((int) this.timeout.getSeconds())));

		this.webClient = WebClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, accessToken)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.filter(ExchangeFilterFunction.ofResponseProcessor(resp -> {
					if (resp.statusCode().isError()) {
						return resp.bodyToMono(String.class)
								.defaultIfEmpty("")
								.flatMap(body -> Mono.error(new RuntimeException(
										"PagueloFacil HTTP " + resp.statusCode() + " body=" + body
										)));
					}
					return Mono.just(resp);
				}))
				.build();
	}

	/**
	 * Retry para cortes de red transitorios (típicos en el sandbox de PF).
	 * Las consultas son idempotentes de solo lectura, así que reintentar
	 * es seguro. No reintenta errores HTTP de negocio (4xx/5xx).
	 */
	private static Retry transientRetry() {
		return Retry.backoff(3, Duration.ofSeconds(2))
				.maxBackoff(Duration.ofSeconds(10))
				.filter(ex ->
				ex instanceof ClosedChannelException
				|| ex instanceof PrematureCloseException
				|| ex instanceof TimeoutException
				|| ex instanceof WebClientRequestException)
				.onRetryExhaustedThrow((spec, signal) -> signal.failure());
	}

	public JsonNode createActivity(CreateActivityRequest request) {
		return webClient.post()
				.uri(ACTIVITIES_PATH)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.timeout(timeout)
				.retryWhen(transientRetry())
				.subscribeOn(Schedulers.boundedElastic())
				.block();
	}

	public JsonNode queryActivities(String conditional) {
		return webClient.get()
				.uri(uriBuilder -> uriBuilder
						.path(ACTIVITIES_PATH)
						.queryParam("conditional", conditional)
						.build())
				.retrieve()
				.bodyToMono(JsonNode.class)
				.timeout(timeout)
				.retryWhen(transientRetry())
				.subscribeOn(Schedulers.boundedElastic())
				.block();
	}

	/** Helpers de extracción robusta */
	/*
	 * public Long tryExtractIdActivity(JsonNode createResponse) { // Intentamos
	 * localizar un campo idActivity en cualquier nivel razonable. // Ej:
	 * data.idActivity, idActivity, data[0].idActivity, etc. if (createResponse ==
	 * null) return null;
	 * 
	 * JsonNode candidates[] = new JsonNode[] {
	 * createResponse.path("data").path("idActivity"),
	 * createResponse.path("idActivity"),
	 * createResponse.path("data").path(0).path("idActivity") };
	 * 
	 * for (JsonNode n : candidates) { if (n != null && n.isNumber()) return
	 * n.asLong(); if (n != null && n.isTextual()) { try { return
	 * Long.parseLong(n.asText()); } catch (Exception ignored) {} } } return null; }
	 */

	public Long tryExtractIdActivity(JsonNode root) {
		if (root == null) return null;

		JsonNode found = findFirstByFieldName(root, "idActivity");
		if (found == null || found.isMissingNode() || found.isNull()) return null;

		if (found.isNumber()) return found.asLong();
		if (found.isTextual()) {
			try { return Long.parseLong(found.asText().trim()); } catch (Exception ignored) {}
		}
		return null;
	}

	private JsonNode findFirstByFieldName(JsonNode node, String fieldName) {
		if (node == null) return null;

		if (node.isObject()) {
			// match directo
			JsonNode direct = node.get(fieldName);
			if (direct != null) return direct;

			// buscar en hijos
			var it = node.fields();
			while (it.hasNext()) {
				var entry = it.next();
				JsonNode found = findFirstByFieldName(entry.getValue(), fieldName);
				if (found != null) return found;
			}
		} else if (node.isArray()) {
			for (JsonNode child : node) {
				JsonNode found = findFirstByFieldName(child, fieldName);
				if (found != null) return found;
			}
		}
		return null;
	}


	public boolean isPaidActivityQuery(JsonNode queryResponse) {
		// Criterio: success=true y data contiene al menos 1 item (o un objeto) indicando status 2.
		if (queryResponse == null) return false;
		boolean success = queryResponse.path("success").asBoolean(false);
		if (!success) return false;

		JsonNode data = queryResponse.path("data");
		if (data.isArray()) return data.size() > 0;
		if (data.isObject()) return data.size() > 0;
		return false;
	}

	/**
	 * Verifica server-to-server si una operación está realmente pagada en PF.
	 *
	 * Es la DEFENSA PRINCIPAL del flujo de pago: como el webhook de PF no
	 * ofrece firma ni IPs documentadas, no confiamos en su contenido —
	 * preguntamos directamente a PF por el codOper.
	 *
	 * @param codOper código de operación recibido (Oper/codOper)
	 * @return true solo si PF confirma la operación como pagada
	 */
	public boolean isOperationPaid(String codOper) {
		if (codOper == null || codOper.isBlank()) return false;
		try {
			JsonNode response = queryActivities("oper::" + codOper);
			boolean paid = isPaidActivityQuery(response);
			// Log temporal de diagnóstico: ver qué devuelve PF realmente
			log.info("Verificación PF codOper={}: paid={}, respuesta={}", codOper, paid, response);
			return paid;
		} catch (Exception e) {
			log.warn("No se pudo verificar operación {} con PF: {}", codOper, e.getMessage());
			return false; // fail-closed
		}
	}

}

