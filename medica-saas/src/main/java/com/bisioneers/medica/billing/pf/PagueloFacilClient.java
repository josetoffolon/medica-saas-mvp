package com.bisioneers.medica.billing.pf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bisioneers.medica.billing.pf.dto.CreateActivityRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class PagueloFacilClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String ACTIVITIES_PATH = "/PFManagementServices/api/v1/Activities/";

    public PagueloFacilClient(
            @Value("${paguelofacil.base-url}") String baseUrl,
            @Value("${paguelofacil.access-token}") String accessToken,
            @Value("${paguelofacil.timeout-ms:8000}") long timeoutMs,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, accessToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector())
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

        // Timeout a nivel de llamada (por método).
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    private final Duration timeout;

    /** Crea una Activity (pago pendiente). Devuelve el JSON completo para guardarlo y extraer idActivity. */
    public JsonNode createActivity(CreateActivityRequest request) {
        return webClient.post()
                .uri(ACTIVITIES_PATH)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .block();
    }

    /**
     * Consulta Activities por conditional.
     * Para validar pagado: conditional=status::2|idActivity::{id}
     */
    public JsonNode queryActivities(String conditional) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ACTIVITIES_PATH) // ojo: doc muestra sin trailing slash a veces; aquí mantenemos el path base
                        .queryParam("conditional", conditional)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
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
}

