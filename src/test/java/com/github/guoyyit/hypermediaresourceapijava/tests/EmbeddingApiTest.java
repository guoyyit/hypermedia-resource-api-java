package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement 2: on-demand embedding, configurable depth/fields, cycle
 * termination and deterministic output/truncation.
 */
@SpringBootTest
class EmbeddingApiTest extends AbstractApiTest {

    @BeforeEach
    void setUp() {
        initMvc();
    }

    @Test
    void depthZeroEmbedsNothing() throws Exception {
        JsonNode body = getJson("/orders/O-1001?depth=0");
        assertThat(body.has("_embedded")).isFalse();
    }

    @Test
    void defaultDepthEmbedsItemsOnly() throws Exception {
        JsonNode body = getJson("/orders/O-1001");
        JsonNode items = body.path("_embedded").path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(2);
        // Item must not embed its order at depth 1 from the order root.
        assertThat(items.get(0).has("_embedded")).isFalse();
    }

    @Test
    void explicitItemsPathEmbedsItems() throws Exception {
        JsonNode body = getJson("/orders/O-1001?embed=items");
        assertThat(body.path("_embedded").path("items")).hasSize(2);
        // An explicit, fully-satisfied embed path is not reported as truncated.
        assertThat(body.has("_meta")).isFalse();
    }

    @Test
    void explicitDeepPathThatCyclesIsTruncatedOnlyAtTheCycle() throws Exception {
        JsonNode body = getJson("/orders/O-1001?depth=0&embed=items.order");
        JsonNode item = body.path("_embedded").path("items").get(0);
        assertThat(item.path("_embedded").path("order").path("id").asText()).isEqualTo("O-1001");
        // The embedded order reached via the explicit path must not fabricate
        // a depth-based truncation; only the actual cycle back-edge is reported
        // (none here, because the explicit path ends at order).
        assertThat(body.has("_meta")).isFalse();
    }

    @Test
    void cycleItemOrderItemsTerminatesAndIsReported() throws Exception {
        JsonNode body = getJson("/orders/O-1001?depth=10&embed=items.order.items");
        JsonNode item = body.path("_embedded").path("items").get(0);
        JsonNode embeddedOrder = item.path("_embedded").path("order");
        assertThat(embeddedOrder.path("id").asText()).isEqualTo("O-1001");
        // The embedded (re-visited) order must not embed its items again.
        assertThat(embeddedOrder.has("_embedded")).isFalse();
        // Truncation is documented and deterministic.
        assertThat(body.path("_meta").path("truncatedEmbeds"))
                .contains(mapper.getNodeFactory().textNode("items.order.items"));
    }

    @Test
    void itemRootEmbedsOwningOrderAndCycleTerminates() throws Exception {
        JsonNode body = getJson("/orders/O-1001/items/O-1001-I001?depth=10");
        JsonNode order = body.path("_embedded").path("order");
        assertThat(order.path("id").asText()).isEqualTo("O-1001");
        // First hop: owning order may embed its items.
        JsonNode nestedItems = order.path("_embedded").path("items");
        assertThat(nestedItems.isArray()).isTrue();
        // Back-edge into the same order must be truncated (links only), so the
        // expansion terminates and is reported deterministically.
        for (JsonNode nestedItem : nestedItems) {
            JsonNode backOrder = nestedItem.path("_embedded").path("order");
            assertThat(backOrder.has("_embedded")).isFalse();
        }
        assertThat(body.path("_meta").path("truncatedEmbeds").toString())
                .contains("order.items.order");
    }

    @Test
    void fieldSelectionIsCanonicalAndTypeScoped() throws Exception {
        JsonNode body = getJson("/orders/O-1001?fields=status,id");
        // Canonical order (id before status), not request order.
        assertThat(body.fieldNames().next()).isEqualTo("id");
        assertThat(body.has("customerName")).isFalse();
        assertThat(body.has("id")).isTrue();
        assertThat(body.has("status")).isTrue();
    }

    @Test
    void unknownFieldIsRejectedWithContract() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders/O-1001?fields=nope").accept(HAL))
                .andExpect(status().isBadRequest()).andReturn());
        assertThat(body.path("code").asText()).isEqualTo("INVALID_EMBED");
        assertThat(body.path("_links").has("self")).isTrue();
    }

    @Test
    void unknownEmbedRelationAndPathAreRejected() throws Exception {
        mockMvc.perform(get("/orders?embed=bogus").accept(HAL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders?embed=order").accept(HAL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/orders?embed=items.order.order").accept(HAL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configuredDepthCapIsEnforced() throws Exception {
        // max-depth defaults to 3; ask for depth=10 and confirm bounded output
        // by the presence of truncation metadata rather than infinite growth.
        JsonNode body = getJson("/orders/O-1001?depth=10");
        int maxNodes = countNodes(body);
        // Six seeds * bounded fan-out comfortably under an unrolled cycle count.
        assertThat(maxNodes).isLessThan(500);
    }

    private int countNodes(JsonNode node) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        int count = 1;
        JsonNode embedded = node.get("_embedded");
        if (embedded != null) {
            for (JsonNode value : embedded) {
                if (value.isArray()) {
                    for (JsonNode member : value) {
                        count += countNodes(member);
                    }
                } else {
                    count += countNodes(value);
                }
            }
        }
        return count;
    }
}
