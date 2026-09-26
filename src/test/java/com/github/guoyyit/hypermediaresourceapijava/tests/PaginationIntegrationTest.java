package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pagination and stable ordering:
 * <ul>
 *   <li>orders are ordered by createdAt asc, id asc (seed order is
 *       o-1..o-5), so pages slice deterministically;</li>
 *   <li>navigation links exist only when their target exists;</li>
 *   <li>a page beyond the last index is 416 {@code page-out-of-range}, while
 *       an empty but valid collection page is 200 with totalElements 0 — the
 *       two are distinguishable per documentation;</li>
 *   <li>oversized size is capped to hypermedia.max-page-size.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "hypermedia.max-page-size=10")
class PaginationIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode body(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return Json.parse(json, response.getBody());
    }

    @Test
    void firstPageHasNoPrevAndItemsInStableOrder() {
        JsonNode page = body("/orders?page=0&size=2");
        JsonNode links = page.path("_links");
        assertThat(links.has("prev")).isFalse();
        assertThat(links.has("next")).isTrue();
        assertThat(links.has("first")).isTrue();
        assertThat(links.has("last")).isTrue();
        assertThat(links.path("self").path("href").asText()).contains("page=0").contains("size=2");

        JsonNode items = page.path("_embedded").path("items");
        assertThat(items.get(0).path("id").asText()).isEqualTo("o-1");
        assertThat(items.get(1).path("id").asText()).isEqualTo("o-2");
        assertThat(page.path("page").path("number").asInt()).isZero();
        assertThat(page.path("page").path("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void middlePageHasBothPrevAndNext() {
        JsonNode page = body("/orders?page=1&size=2");
        assertThat(page.path("_links").has("prev")).isTrue();
        assertThat(page.path("_links").has("next")).isTrue();
        assertThat(page.path("_embedded").path("items").get(0).path("id").asText()).isEqualTo("o-3");
    }

    @Test
    void lastPageHasNoNext() {
        JsonNode page = body("/orders?page=2&size=2");
        assertThat(page.path("_links").has("next")).isFalse();
        assertThat(page.path("_links").has("prev")).isTrue();
        JsonNode items = page.path("_embedded").path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("id").asText()).isEqualTo("o-5");
    }

    @Test
    void pageBeyondLastIs416WithDocumentedCode() {
        ResponseEntity<String> response = rest.getForEntity("/orders?page=99&size=2", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(416);
        JsonNode error = Json.parse(json, response.getBody());
        assertThat(error.path("error").path("code").asText()).isEqualTo("page-out-of-range");
        assertThat(error.path("_links").has("home")).isTrue();
    }

    @Test
    void emptyCollectionIs200WithDistinguishableEmptyPage() {
        // New customer owns no orders: valid empty page, not an out-of-range page.
        String created = rest.postForEntity("/customers",
                new org.springframework.http.HttpEntity<>("""
                        {"name":"No Orders","email":"none@example.com"}""",
                        jsonContentType()), String.class).getBody();
        String customerId = Json.parse(json, created).path("id").asText();

        ResponseEntity<String> response =
                rest.getForEntity("/customers/" + customerId + "/orders", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode page = Json.parse(json, response.getBody());
        assertThat(page.path("page").path("totalElements").asInt()).isZero();
        assertThat(page.path("page").path("totalPages").asInt()).isZero();
        assertThat(page.path("_embedded").path("items")).hasSize(0);
        assertThat(page.path("_links").has("first")).isTrue();
        assertThat(page.path("_links").has("last")).isTrue();
        assertThat(page.path("_links").has("prev")).isFalse();
        assertThat(page.path("_links").has("next")).isFalse();
    }

    @Test
    void sizeLargerThanMaximumIsCapped() {
        JsonNode page = body("/orders?page=0&size=1000");
        // cap is 10 for this test slice; all 5 seed orders fit the first page.
        assertThat(page.path("page").path("size").asInt()).isEqualTo(10);
        assertThat(page.path("_embedded").path("items")).hasSize(5);
    }

    @Test
    void invalidPageParameterIs422() {
        ResponseEntity<String> response = rest.getForEntity("/orders?page=-1", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(Json.parse(json, response.getBody()).path("error").path("code").asText())
                .isEqualTo("invalid-request");
    }

    private static org.springframework.http.HttpHeaders jsonContentType() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
