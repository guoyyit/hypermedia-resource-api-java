package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement 3: a genuinely empty collection must be distinguishable from an
 * out-of-range page: empty -> 200 + pageState EMPTY on page 1; out of range
 * -> 404 + pageState OUT_OF_RANGE.
 */
@SpringBootTest
@TestPropertySource(properties = "hypermedia.data.seed-on-startup=false")
class EmptyCollectionApiTest extends AbstractApiTest {

    @BeforeEach
    void setUp() {
        initMvc();
    }

    @Test
    void emptyCollectionFirstPageIs200EmptyWithNavigation() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders?page=1&size=20").accept(HAL))
                .andExpect(status().isOk()).andReturn());
        assertThat(body.path("pageState").asText()).isEqualTo("EMPTY");
        assertThat(body.path("empty").asBoolean()).isTrue();
        assertThat(body.path("totalItems").asInt()).isZero();
        assertThat(body.path("_embedded").path("orders")).hasSize(0);
        // Even an empty collection advertises first/last and the create control.
        assertThat(body.path("_links").has("first")).isTrue();
        assertThat(body.path("_links").has("last")).isTrue();
        assertThat(body.path("_links").has("create")).isTrue();
        assertThat(body.path("_links").has("prev")).isFalse();
        assertThat(body.path("_links").has("next")).isFalse();
    }

    @Test
    void outOfRangeOnEmptyStoreIs404OutOfRange() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders?page=2&size=20").accept(HAL))
                .andExpect(status().isNotFound()).andReturn());
        assertThat(body.path("pageState").asText()).isEqualTo("OUT_OF_RANGE");
        assertThat(body.path("_embedded").path("orders")).hasSize(0);
    }
}
