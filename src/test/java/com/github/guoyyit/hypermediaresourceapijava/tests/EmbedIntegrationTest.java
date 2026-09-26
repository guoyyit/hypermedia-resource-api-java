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
 * Embedding semantics:
 * <ul>
 *   <li>no embed => no _embedded block beyond HAL omission (empty maps are not
 *       rendered by the writer);</li>
 *   <li>named relations embed deterministic resources in fixed order;</li>
 *   <li>multi-segment paths (items.product) expand nested resources;</li>
 *   <li>cycles (customer -> orders -> customer -> orders ...) terminate;</li>
 *   <li>embed-depth above the configured cap is truncated deterministically;</li>
 *   <li>fields selects a deterministic subset of top-level properties.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmbedIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode get(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return Json.parse(json, response.getBody());
    }

    @Test
    void noEmbedParameterYieldsNoEmbeddedBlock() {
        JsonNode order = get("/orders/o-3");
        assertThat(order.has("_embedded")).isFalse();
    }

    @Test
    void embedsCustomerOnDemandWithItsState() {
        JsonNode order = get("/orders/o-3?embed=customer");
        JsonNode customer = order.path("_embedded").path("customer");
        assertThat(customer.isArray()).isTrue();
        assertThat(customer.get(0).path("id").asText()).isEqualTo("c-2");
        assertThat(customer.get(0).path("name").asText()).isEqualTo("Bob Brown");
    }

    @Test
    void embedsItemsAndNestedProductInDeterministicOrder() {
        JsonNode order = get("/orders/o-3?embed=items.product");
        JsonNode items = order.path("_embedded").path("items");
        assertThat(items).hasSize(2);
        // Line order is insertion order from the seed data.
        assertThat(items.get(0).path("productId").asText()).isEqualTo("p-3");
        assertThat(items.get(1).path("productId").asText()).isEqualTo("p-1");
        JsonNode firstProduct = items.get(0).path("_embedded").path("product").get(0);
        assertThat(firstProduct.path("id").asText()).isEqualTo("p-3");
        assertThat(firstProduct.path("name").asText()).isEqualTo("Gizmo");
    }

    @Test
    void cyclicEmbedChainTerminatesInsteadOfRecursing() {
        // depth 2: customer -> orders -> customer must not embed customer again.
        JsonNode customer = get("/customers/c-1?embed=orders.customer");
        JsonNode orders = customer.path("_embedded").path("orders");
        assertThat(orders.isArray()).isTrue();
        assertThat(orders).isNotEmpty();
        for (JsonNode order : orders) {
            // The cycle edge order->customer must remain a link, not an embed.
            assertThat(order.has("_embedded")).isFalse();
            assertThat(order.path("_links").path("customer").path("href").asText())
                    .endsWith("/customers/c-1");
        }
    }

    @Test
    void embedRelationOrderFollowsFirstOccurrenceInQuery() {
        JsonNode order = get("/orders/o-3?embed=items.product,customer");
        java.util.Iterator<String> keys = order.path("_embedded").fieldNames();
        assertThat(keys.next()).isEqualTo("items");
        assertThat(keys.next()).isEqualTo("customer");
    }

    @Test
    void depthBudgetTruncatesDeeperExpansion() {
        // embed-depth=1 allows customer expansion but not customer.orders.
        JsonNode shallow = get("/customers/c-1?embed=orders&embed-depth=1");
        // "orders" is depth 1 from customer root, should be present.
        assertThat(shallow.path("_embedded").path("orders").isArray()).isTrue();

        JsonNode truncated = get("/customers/c-1?embed=orders.customer&embed-depth=1");
        JsonNode orders = truncated.path("_embedded").path("orders");
        for (JsonNode order : orders) {
            assertThat(order.has("_embedded")).isFalse();
        }
    }

    @Test
    void fieldsSelectsSubsetOfProperties() {
        JsonNode order = get("/orders/o-1?fields=id,state");
        assertThat(order.has("id")).isTrue();
        assertThat(order.has("state")).isTrue();
        assertThat(order.has("customerId")).isFalse();
        assertThat(order.has("items")).isFalse();
        assertThat(order.path("_links").has("self")).isTrue();
    }

    @Test
    void unknownEmbedRelationIsIgnored() {
        ResponseEntity<String> response = rest.getForEntity("/orders/o-1?embed=nonsense", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode body = Json.parse(json, response.getBody());
        assertThat(body.path("_embedded").has("nonsense")).isFalse();
    }
}
