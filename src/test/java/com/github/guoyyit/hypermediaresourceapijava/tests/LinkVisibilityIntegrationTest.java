package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transition links must appear only when legal for the current state, and an
 * invalid transition must be rejected without a state change. Uses fresh
 * orders created per test to avoid seed-state coupling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkVisibilityIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private String createFreshOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"customerId":"c-1","items":[{"productId":"p-1","quantity":1}]}
                """;
        ResponseEntity<String> response = rest.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return Json.readId(response.getBody());
    }

    private JsonNode linksOf(String orderId) {
        ResponseEntity<String> response = rest.getForEntity("/orders/" + orderId, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return Json.parse(json, response.getBody()).path("_links");
    }

    private JsonNode getOrder(String orderId) {
        ResponseEntity<String> response = rest.getForEntity("/orders/" + orderId, String.class);
        return Json.parse(json, response.getBody());
    }

    private ResponseEntity<String> act(String orderId, String action, String idempotencyKey,
                                       String ifMatch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        if (ifMatch != null) {
            headers.set(HttpHeaders.IF_MATCH, ifMatch);
        }
        return rest.exchange("/orders/" + orderId + "/transitions", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"" + action + "\"}", headers), String.class);
    }

    @Test
    void createdOrderExposesOnlyPayAndCancelTransitions() {
        String id = createFreshOrder();
        JsonNode links = linksOf(id);
        assertThat(links.has("pay")).isTrue();
        assertThat(links.has("cancel")).isTrue();
        assertThat(links.has("ship")).isFalse();
        assertThat(links.has("deliver")).isFalse();
        assertThat(links.path("self").path("href").asText()).endsWith("/orders/" + id);
    }

    @Test
    void linksChangeAsStateAdvancesThroughFullLifecycle() {
        String id = createFreshOrder();

        assertThat(act(id, "pay", null, null).getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode paid = linksOf(id);
        assertThat(paid.has("pay")).isFalse();
        assertThat(paid.has("ship")).isTrue();
        assertThat(paid.has("cancel")).isTrue();

        assertThat(act(id, "ship", null, null).getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode shipped = linksOf(id);
        assertThat(shipped.has("ship")).isFalse();
        assertThat(shipped.has("cancel")).isFalse();
        assertThat(shipped.has("deliver")).isTrue();

        assertThat(act(id, "deliver", null, null).getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode delivered = linksOf(id);
        assertThat(delivered.has("deliver")).isFalse();
        assertThat(delivered.has("pay")).isFalse();
        assertThat(delivered.has("ship")).isFalse();
        assertThat(delivered.has("cancel")).isFalse();

        assertThat(getOrder(id).path("state").asText()).isEqualTo("delivered");
    }

    @Test
    void illegalTransitionReturns409WithCurrentLinkAndKeepsState() {
        String id = createFreshOrder();
        ResponseEntity<String> response = act(id, "ship", null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode error = Json.parse(json, response.getBody());
        assertThat(error.path("error").path("code").asText()).isEqualTo("illegal-transition");
        assertThat(error.path("error").path("retryable").asBoolean()).isTrue();
        assertThat(error.path("_links").path("current").path("href").asText())
                .endsWith("/orders/" + id);
        assertThat(getOrder(id).path("state").asText()).isEqualTo("created");
    }

    @Test
    void cancelledOrderExposesNoTransitions() {
        String id = createFreshOrder();
        assertThat(act(id, "cancel", null, null).getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode links = linksOf(id);
        assertThat(links.has("cancel")).isFalse();
        assertThat(links.has("pay")).isFalse();
        assertThat(links.has("ship")).isFalse();
        assertThat(links.has("deliver")).isFalse();
    }

    @Test
    void repeatedTransitionWithoutKeyDoesNotChangeStateTwice() {
        String id = createFreshOrder();
        assertThat(act(id, "pay", null, null).getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<String> second = act(id, "pay", null, null);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(getOrder(id).path("version").asLong()).isEqualTo(1L);
    }
}
