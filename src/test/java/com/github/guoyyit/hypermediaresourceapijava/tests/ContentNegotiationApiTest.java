package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement 5 & 6: content negotiation, equivalent representations,
 * negotiation-failure contract, and navigable error resources.
 */
@SpringBootTest
class ContentNegotiationApiTest extends AbstractApiTest {

    @BeforeEach
    void setUp() {
        initMvc();
    }

    @Test
    void halLayoutUsesUnderscoreContainers() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders/O-1001").accept(HAL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/hal+json"))
                .andExpect(header().string("Vary", "Accept")).andReturn());
        assertThat(body.has("_links")).isTrue();
        assertThat(body.has("_embedded")).isTrue();
    }

    @Test
    void vendorLayoutUsesDataLinksEmbeddedAndSameSemantics() throws Exception {
        JsonNode hal = json(mockMvc.perform(get("/orders/O-1001").accept(HAL)).andReturn());
        JsonNode vnd = json(mockMvc.perform(get("/orders/O-1001").accept(VND))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.hypermedia+json"))
                .andReturn());
        assertThat(vnd.has("data")).isTrue();
        assertThat(vnd.has("links")).isTrue();
        assertThat(vnd.has("embedded")).isTrue();
        // Semantic equality: same property values and same self link.
        assertThat(vnd.path("data").path("id").asText()).isEqualTo(hal.path("id").asText());
        assertThat(vnd.path("data").path("status").asText()).isEqualTo(hal.path("status").asText());
        assertThat(vnd.path("links").path("self").path("href").asText())
                .isEqualTo(hal.path("_links").path("self").path("href").asText());
        // Same available controls, independent of wire layout.
        vnd.path("links").fieldNames().forEachRemaining(rel ->
                assertThat(hal.path("_links").has(rel)).isTrue());
    }

    @Test
    void genericApplicationJsonMapsToHalShape() throws Exception {
        mockMvc.perform(get("/orders/O-1001").accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/hal+json"));
    }

    @Test
    void qValuesChooseHighestSupportedPreference() throws Exception {
        // Unsupported xml is ignored; the only supported type (vnd, q=0.2) wins.
        mockMvc.perform(get("/orders/O-1001")
                        .header("Accept", "application/xml;q=0.9, application/vnd.hypermedia+json;q=0.2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.hypermedia+json"));
        mockMvc.perform(get("/orders/O-1001")
                        .header("Accept", "application/xml;q=0.1, application/vnd.hypermedia+json;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.hypermedia+json"));
        // A supported type explicitly given q=0 is refused.
        mockMvc.perform(get("/orders/O-1001")
                        .header("Accept", "application/hal+json;q=0, application/xml;q=0.9"))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void unsupportedPreferenceReturnsNegotiationFailureContract() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders/O-1001")
                        .header("Accept", "application/xml"))
                .andExpect(status().isNotAcceptable()).andReturn());
        assertThat(body.path("code").asText()).isEqualTo("NEGOTIATION_FAILED");
        assertThat(body.has("detail")).isTrue();
        assertThat(body.path("_links").has("self")).isTrue();
        assertThat(body.path("_links").has("orders")).isTrue();
        // Negotiation failures are still served in the default HAL shape.
        assertThat(body.has("_links")).isTrue();
    }

    @Test
    void missingAcceptUsesConfiguredDefault() throws Exception {
        mockMvc.perform(get("/orders/O-1001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/hal+json"));
    }

    @Test
    void notFoundIsNavigableErrorResourceWithoutInternals() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders/UNKNOWN").accept(HAL))
                .andExpect(status().isNotFound()).andReturn());
        assertThat(body.path("code").asText()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(body.path("status").asInt()).isEqualTo(404);
        assertThat(body.has("type")).isTrue();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.path("_links").has("self")).isTrue();
        assertThat(body.path("_links").has("orders")).isTrue();
        // Never leak implementation details.
        assertThat(body.toString()).doesNotContain("Exception", "com.github.", "at java.");
    }

    @Test
    void unknownPathAndMethodNotAllowedAreNavigable() throws Exception {
        JsonNode path = json(mockMvc.perform(get("/does/not/exist").accept(HAL))
                .andExpect(status().isNotFound()).andReturn());
        assertThat(path.path("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(path.path("_links").has("home")).isTrue();

        JsonNode method = json(mockMvc.perform(delete("/orders/O-1001").accept(HAL))
                .andExpect(status().isMethodNotAllowed()).andReturn());
        assertThat(method.path("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(method.path("_links").has("orders")).isTrue();
    }

    @Test
    void malformedBodyAndValidationErrorsAreDocumented() throws Exception {
        JsonNode malformed = json(mockMvc.perform(post("/orders")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{not-json").accept(HAL))
                .andExpect(status().isBadRequest()).andReturn());
        assertThat(malformed.path("code").asText()).isEqualTo("MALFORMED_REQUEST");
        assertThat(malformed.path("_links").has("self")).isTrue();

        JsonNode invalid = json(mockMvc.perform(post("/orders")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"\",\"items\":[]}").accept(HAL))
                .andExpect(status().isBadRequest()).andReturn());
        assertThat(invalid.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }
}
