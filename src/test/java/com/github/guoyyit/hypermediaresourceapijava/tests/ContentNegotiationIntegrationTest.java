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
 * Content negotiation:
 * <ul>
 *   <li>no Accept / &#42;/&#42; defaults to HAL JSON;</li>
 *   <li>explicit HAL and plain hypermedia JSON both return 200 with the right
 *       Content-Type and semantically identical state;</li>
 *   <li>application/json is accepted and served as HAL;</li>
 *   <li>an unsupported preference yields the documented 406 contract listing
 *       available representations;</li>
 *   <li>switching representation never changes resource state or semantics.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContentNegotiationIntegrationTest {

    private static final String HAL = "application/prs.hal-custom+json";
    private static final String PLAIN = "application/vnd.hypermedia+json";

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private ResponseEntity<String> get(String path, String accept) {
        HttpHeaders headers = new HttpHeaders();
        if (accept != null) {
            headers.set(HttpHeaders.ACCEPT, accept);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void wildcardAndApplicationJsonProduceHalShape() {
        ResponseEntity<String> wildcard = get("/orders/o-1", MediaType.ALL_VALUE);
        assertThat(wildcard.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(wildcard.getHeaders().getContentType().toString()).startsWith(HAL);
        assertThat(wildcard.getBody()).contains("\"_links\"");

        // application/json is the HAL-compatible alias: HAL shape, JSON label.
        ResponseEntity<String> jsonType = get("/orders/o-1", MediaType.APPLICATION_JSON_VALUE);
        assertThat(jsonType.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON))
                .isTrue();
        assertThat(jsonType.getBody()).contains("\"_links\"");
    }

    @Test
    void explicitHalAndPlainAreBothServed() {
        ResponseEntity<String> hal = get("/orders/o-1", HAL);
        ResponseEntity<String> plain = get("/orders/o-1", PLAIN);

        assertThat(hal.getHeaders().getContentType().toString()).startsWith(HAL);
        assertThat(plain.getHeaders().getContentType().toString()).startsWith(PLAIN);

        JsonNode halBody = Json.parse(json, hal.getBody());
        JsonNode plainBody = Json.parse(json, plain.getBody());

        // Same semantic state, different structural containers.
        assertThat(halBody.path("id").asText())
                .isEqualTo(plainBody.path("state").path("id").asText());
        assertThat(halBody.path("state").asText())
                .isEqualTo(plainBody.path("state").path("state").asText());
        assertThat(halBody.path("version").asLong())
                .isEqualTo(plainBody.path("state").path("version").asLong());

        // Links carry the same relations and targets.
        assertThat(halBody.path("_links").path("self").path("href").asText())
                .isEqualTo(findPlainLink(plainBody, "self"));
        assertThat(halBody.path("_links").path("pay").path("href").asText())
                .isEqualTo(findPlainLink(plainBody, "pay"));
    }

    @Test
    void unsupportedMediaTypeReturns406Contract() {
        ResponseEntity<String> response = get("/orders/o-1", "application/xml");
        assertThat(response.getStatusCode().value()).isEqualTo(406);
        JsonNode error = Json.parse(json, response.getBody());
        assertThat(error.path("error").path("code").asText())
                .isEqualTo("content-negotiation-failed");
        assertThat(error.path("error").path("status").asInt()).isEqualTo(406);
        JsonNode available = error.path("error").path("details").path("availableRepresentations");
        assertThat(available.isArray()).isTrue();
        assertThat(available.toString()).contains(HAL).contains(PLAIN);
        assertThat(error.path("_links").has("home")).isTrue();
    }

    @Test
    void representationSwitchDoesNotChangeState() {
        JsonNode before = Json.parse(json, get("/orders/o-2", HAL).getBody());
        for (int i = 0; i < 3; i++) {
            JsonNode other = Json.parse(json, get("/orders/o-2", PLAIN).getBody());
            assertThat(other.path("state").path("version").asLong())
                    .isEqualTo(before.path("version").asLong());
            assertThat(other.path("state").path("state").asText())
                    .isEqualTo(before.path("state").asText());
        }
        JsonNode after = Json.parse(json, get("/orders/o-2", HAL).getBody());
        assertThat(after.path("version").asLong()).isEqualTo(before.path("version").asLong());
    }

    @Test
    void errorBodyHonoursNegotiatedPlainType() {
        ResponseEntity<String> response = get("/orders/does-not-exist", PLAIN);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType().toString()).startsWith(PLAIN);
        JsonNode body = Json.parse(json, response.getBody());
        // Plain shape: links array instead of _links object.
        assertThat(body.has("links")).isTrue();
        assertThat(body.path("error").path("code").asText()).isEqualTo("resource-not-found");
    }

    private String findPlainLink(JsonNode plainBody, String rel) {
        for (JsonNode link : plainBody.path("links")) {
            if (rel.equals(link.path("rel").asText())) {
                return link.path("href").asText();
            }
        }
        return null;
    }
}
