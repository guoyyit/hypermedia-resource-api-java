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
 * Every error is itself a navigable hypermedia resource:
 * <ul>
 *   <li>has an "error" object with the documented member order/fields;</li>
 *   <li>has a self link and a home link;</li>
 *   <li>retryable failures (404/409/412/416) expose a safe retry/current
 *       link, non-retryable ones do not;</li>
 *   <li>no stack traces, class names or internal details leak.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorContractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode expect(String path, int expectedStatus) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        return Json.parse(json, response.getBody());
    }

    @Test
    void notFoundIsNavigableAndDoesNotLeakInternals() {
        JsonNode error = expect("/orders/missing-xyz", 404);
        JsonNode e = error.path("error");
        assertThat(e.path("status").asInt()).isEqualTo(404);
        assertThat(e.path("code").asText()).isEqualTo("resource-not-found");
        assertThat(e.path("message").asText()).contains("missing-xyz");
        assertThat(e.path("retryable").asBoolean()).isTrue();
        assertThat(error.path("_links").path("self").path("href").asText())
                .isEqualTo("/orders/missing-xyz");
        assertThat(error.path("_links").path("home").path("href").asText()).isEqualTo("/");
        assertThat(error.toString()).doesNotContain("Exception").doesNotContain("java.");
    }

    @Test
    void conflictErrorLinksCurrentOrderState() {
        String order = createOrder();
        // deliver illegal from CREATED
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(
                "/orders/" + order + "/transitions", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"deliver\"}", headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("illegal-transition");
        assertThat(body.path("_links").path("current").path("href").asText())
                .endsWith("/orders/" + order);
        assertThat(body.path("_links").path("retry").path("href").asText())
                .endsWith("/orders/" + order);
    }

    @Test
    void preconditionFailureIsRetryableAndNavigable() {
        String order = createOrder();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.IF_MATCH, "\"" + order + ":v123\"");
        ResponseEntity<String> response = rest.exchange(
                "/orders/" + order + "/transitions", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"pay\"}", headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(412);
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("precondition-failed");
        assertThat(body.path("error").path("retryable").asBoolean()).isTrue();
        assertThat(body.path("_links").has("current")).isTrue();
    }

    @Test
    void invalidRequestBodyIs422NotRetryable() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>("{\"customerId\":\"c-1\"}", headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("invalid-request");
        assertThat(body.path("error").path("retryable").asBoolean()).isFalse();
        assertThat(body.path("_links").has("retry")).isFalse();
    }

    @Test
    void pageOutOfRangeIs416WithCode() {
        JsonNode body = expect("/products?page=50", 416);
        assertThat(body.path("error").path("code").asText()).isEqualTo("page-out-of-range");
        assertThat(body.path("_links").has("self")).isTrue();
        assertThat(body.path("_links").has("home")).isTrue();
    }

    @Test
    void negotiationFailureListsAlternatives() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "text/csv");
        ResponseEntity<String> response = rest.exchange("/products/p-1", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(406);
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("error").path("code").asText())
                .isEqualTo("content-negotiation-failed");
        assertThat(body.path("error").path("details").path("availableRepresentations").toString())
                .contains("application/prs.hal-custom+json")
                .contains("application/vnd.hypermedia+json");
    }

    @Test
    void preconditionRequiredOnPutWithoutIfMatch() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/customers/c-1", HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"X\",\"email\":\"x@example.com\"}", headers),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(428);
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("precondition-required");
        assertThat(body.path("_links").has("current")).isTrue();
    }

    @Test
    void errorFromTransitionTargetPointsSelfAtTransitionAndCurrentAtOrder() {
        String order = createOrder();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(
                "/orders/" + order + "/transitions", HttpMethod.POST,
                new HttpEntity<>("{\"action\":\"ship\"}", headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("_links").path("self").path("href").asText())
                .endsWith("/transitions");
        assertThat(body.path("_links").path("current").path("href").asText())
                .doesNotContain("transitions");
    }

    private String createOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>("""
                        {"customerId":"c-1","items":[{"productId":"p-1","quantity":1}]}
                        """, headers), String.class);
        return Json.parse(json, response.getBody()).path("id").asText();
    }
}
