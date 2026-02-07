package com.bisioneers.medica.billing.pf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@Component
public class PagueloFacilLinkClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String endpoint;
    private final String cclw;
    private final int expiresInSeconds;

    public PagueloFacilLinkClient(
            @Value("${paguelofacil.link.base-url}") String baseUrl,
            @Value("${paguelofacil.link.endpoint}") String endpoint,
            @Value("${paguelofacil.cclw}") String cclw,
            @Value("${paguelofacil.expires-in:1800}") int expiresInSeconds,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.endpoint = endpoint;
        this.cclw = cclw;
        this.expiresInSeconds = expiresInSeconds;
        this.objectMapper = objectMapper;
    }

    public CreateLinkResult createPaymentLink(CreateLinkCommand cmd) {
        // PF requiere RETURN_URL en HEX (según doc)
        String returnUrlHex = toHex(cmd.returnUrl());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("CCLW", cclw);
        form.add("CMTN", cmd.amount()); // "100.00"
        form.add("CDSC", cmd.description());
        form.add("RETURN_URL", returnUrlHex);
        form.add("EXPIRES_IN", String.valueOf(expiresInSeconds));

        // Correlación
        form.add("PARM_1", cmd.parm1());  // transactionId UUID
        if (cmd.parm2() != null && !cmd.parm2().isBlank()) form.add("PARM_2", cmd.parm2());

        // También puedes enviar Webhook URL si PF lo acepta por parámetro (algunas integraciones lo configuran en portal)
        // Si en tu doc aparece, se agrega aquí.

        String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            boolean success = json.path("success").asBoolean(false);
            if (!success) {
                String msg = json.path("message").asText("Unknown PF error");
                throw new IllegalStateException("PagueloFacil create link failed: " + msg + " raw=" + responseBody);
            }
            String url = json.path("data").path("url").asText(null);
            String code = json.path("data").path("code").asText(null);
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("PagueloFacil create link: missing data.url raw=" + responseBody);
            }
            return new CreateLinkResult(url, code, responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("PagueloFacil create link: cannot parse response raw=" + responseBody, e);
        }
    }

    private static String toHex(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public record CreateLinkCommand(
            String amount,      // "100.00"
            String description,
            String returnUrl,
            String parm1,
            String parm2
    ) {}

    public record CreateLinkResult(
            String checkoutUrl,
            String code,
            String rawJson
    ) {}
}
