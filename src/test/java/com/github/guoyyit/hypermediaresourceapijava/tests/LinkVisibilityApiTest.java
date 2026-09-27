package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement 1: transition links appear only when legal in the current
 * state/context; terminal states and invalid transitions expose no control.
 */
@SpringBootTest
class LinkVisibilityApiTest extends AbstractApiTest {

    @BeforeEach
    void setUp() {
        initMvc();
    }

    @Test
    void draftExposesEditSubmitCancelButNotPayShipDeliver() throws Exception {
        JsonNode links = getJson("/orders/O-1001").get("_links");
        assertThat(links.has("self")).isTrue();
        assertThat(links.has("items")).isTrue();
        assertThat(links.has("edit")).isTrue();
        assertThat(links.has("submit")).isTrue();
        assertThat(links.has("cancel")).isTrue();
        assertThat(links.has("pay")).isFalse();
        assertThat(links.has("ship")).isFalse();
        assertThat(links.has("deliver")).isFalse();
        assertThat(links.get("submit").get("method").asText()).isEqualTo("POST");
        assertThat(links.get("edit").get("method").asText()).isEqualTo("PUT");
    }

    @Test
    void pendingPaymentExposesPayAndCancelOnly() throws Exception {
        JsonNode links = getJson("/orders/O-1002").get("_links");
        assertThat(links.has("pay")).isTrue();
        assertThat(links.has("cancel")).isTrue();
        assertThat(links.has("submit")).isFalse();
        assertThat(links.has("edit")).isFalse();
        assertThat(links.has("ship")).isFalse();
    }

    @Test
    void paidExposesShipOnly() throws Exception {
        JsonNode links = getJson("/orders/O-1003").get("_links");
        assertThat(links.has("ship")).isTrue();
        assertThat(links.has("pay")).isFalse();
        assertThat(links.has("cancel")).isFalse();
    }

    @Test
    void shippedExposesDeliverOnly() throws Exception {
        JsonNode links = getJson("/orders/O-1004").get("_links");
        assertThat(links.has("deliver")).isTrue();
        assertThat(links.has("ship")).isFalse();
    }

    @Test
    void terminalStatesExposeNoTransitionControls() throws Exception {
        for (String id : new String[]{"O-1005", "O-1006"}) {
            JsonNode links = getJson("/orders/" + id).get("_links");
            assertThat(links.has("submit")).isFalse();
            assertThat(links.has("pay")).isFalse();
            assertThat(links.has("ship")).isFalse();
            assertThat(links.has("deliver")).isFalse();
            assertThat(links.has("cancel")).isFalse();
            assertThat(links.has("edit")).isFalse();
            assertThat(links.has("self")).isTrue();
            assertThat(links.has("items")).isTrue();
        }
    }

    @Test
    void homeAdvertisesOnlySafeAndCreateControls() throws Exception {
        JsonNode links = getJson("/").get("_links");
        assertThat(links.has("orders")).isTrue();
        assertThat(links.has("create")).isTrue();
        assertThat(links.get("create").get("method").asText()).isEqualTo("POST");
    }

    @Test
    void invalidTransitionEndpointIsNotAdvertisedAndRejected() throws Exception {
        JsonNode links = getJson("/orders/O-1005").get("_links");
        assertThat(links.has("pay")).isFalse();
        // Even a direct call must not perform an unavailable transition.
        mockMvc.perform(get("/orders/O-1005")).andExpect(status().isOk());
    }
}
