package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement 4: ETag conditional reads/updates, concurrent conflict
 * contracts, repeated-transition idempotence and atomic (no intermediate)
 * state.
 */
@SpringBootTest
class ConditionalConcurrencyApiTest extends AbstractApiTest {

    private String createdLocation;
    private String initialEtag;

    @BeforeEach
    void setUp() throws Exception {
        initMvc();
        String body = """
                {"customerName":"Concurrency User","items":[
                  {"sku":"SKU-X","name":"Thing","quantity":1,"unitPrice":100}]}
                """;
        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body).accept(HAL))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andReturn();
        createdLocation = created.getResponse().getHeader("Location");
        initialEtag = created.getResponse().getHeader("ETag");
    }

    @Test
    void ifNoneMatchHitReturns304WithoutBody() throws Exception {
        mockMvc.perform(get(createdLocation).accept(HAL).header("If-None-Match", initialEtag))
                .andExpect(status().isNotModified())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    void ifNoneMatchMissReturns200AndRepresentation() throws Exception {
        mockMvc.perform(get(createdLocation).accept(HAL).header("If-None-Match", "W/\"x:999\""))
                .andExpect(status().isOk());
    }

    @Test
    void updateWithoutIfMatchIs428WithContract() throws Exception {
        JsonNode body = json(mockMvc.perform(put(createdLocation)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("V2"))
                        .accept(HAL))
                .andExpect(status().isPreconditionRequired()).andReturn());
        assertThat(body.path("code").asText()).isEqualTo("PRECONDITION_REQUIRED");
        assertThat(body.path("_links").has("retry")).isTrue();
    }

    @Test
    void updateWithStaleEtagIs412ConflictContract() throws Exception {
        // First successful update bumps the version.
        mockMvc.perform(put(createdLocation).contentType(MediaType.APPLICATION_JSON)
                .content(validBody("V2")).header("If-Match", initialEtag).accept(HAL))
                .andExpect(status().isOk());
        // Reusing the original ETag must fail and must not change anything.
        JsonNode before = getJson(createdLocation);
        JsonNode error = json(mockMvc.perform(put(createdLocation)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("V3"))
                        .header("If-Match", initialEtag).accept(HAL))
                .andExpect(status().isPreconditionFailed()).andReturn());
        assertThat(error.path("code").asText()).isEqualTo("PRECONDITION_FAILED");
        assertThat(error.path("_links").has("retry")).isTrue();
        JsonNode after = getJson(createdLocation);
        assertThat(after.path("customerName").asText())
                .isEqualTo(before.path("customerName").asText());
    }

    @Test
    void successfulUpdateChangesEtagAndNoIntermediateStateOnFailure() throws Exception {
        MvcResult result = mockMvc.perform(put(createdLocation)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("Renamed"))
                        .header("If-Match", initialEtag).accept(HAL))
                .andExpect(status().isOk()).andReturn();
        String newEtag = result.getResponse().getHeader("ETag");
        assertThat(newEtag).isNotEqualTo(initialEtag);
        assertThat(getJson(createdLocation).path("customerName").asText()).isEqualTo("Renamed");

        // An invalid payload must fail validation before any state is touched.
        String invalid = """
                {"customerName":"Broken","items":[
                  {"sku":"","name":"","quantity":0,"unitPrice":-1}]}
                """;
        mockMvc.perform(put(createdLocation).contentType(MediaType.APPLICATION_JSON)
                .content(invalid).header("If-Match", newEtag).accept(HAL))
                .andExpect(status().isBadRequest());
        // State remains the last successfully committed version.
        JsonNode current = getJson(createdLocation);
        assertThat(current.path("customerName").asText()).isEqualTo("Renamed");
        assertThat(etag(fetch(createdLocation))).isEqualTo(newEtag);
    }

    @Test
    void transitionRequiresIfMatchAndBumpsVersionExactlyOnce() throws Exception {
        mockMvc.perform(post(createdLocation + "/transitions/submit")
                        .header("If-Match", initialEtag).accept(HAL))
                .andExpect(status().isOk());
        JsonNode current = getJson(createdLocation);
        assertThat(current.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        // submit link has disappeared.
        assertThat(current.path("_links").has("submit")).isFalse();
        assertThat(current.path("_links").has("pay")).isTrue();
    }

    @Test
    void repeatedTransitionNeverChangesStateTwice() throws Exception {
        mockMvc.perform(post(createdLocation + "/transitions/submit")
                .header("If-Match", initialEtag).accept(HAL)).andExpect(status().isOk());
        // Same ETag again: version conflict (412).
        mockMvc.perform(post(createdLocation + "/transitions/submit")
                .header("If-Match", initialEtag).accept(HAL))
                .andExpect(status().isPreconditionFailed());

        String currentEtag = etag(fetch(createdLocation));
        // Current ETag, but the action is no longer available: 409, state unchanged.
        JsonNode error = json(mockMvc.perform(post(createdLocation + "/transitions/submit")
                        .header("If-Match", currentEtag).accept(HAL))
                .andExpect(status().isConflict()).andReturn());
        assertThat(error.path("code").asText()).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(getJson(createdLocation).path("status").asText())
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void fullLifecycleTransitionsAreGuardedByStatus() throws Exception {
        String etag = initialEtag;
        etag = doTransition("submit", etag, "PENDING_PAYMENT");
        etag = doTransition("pay", etag, "PAID");
        etag = doTransition("ship", etag, "SHIPPED");
        doTransition("deliver", etag, "DELIVERED");
        // Terminal: no transition control remains.
        JsonNode links = getJson(createdLocation).get("_links");
        for (String a : new String[]{"submit", "pay", "ship", "deliver", "cancel"}) {
            assertThat(links.has(a)).isFalse();
        }
    }

    @Test
    void unknownActionIs400AndLeavesStateIntact() throws Exception {
        mockMvc.perform(post(createdLocation + "/transitions/frobnicate")
                .header("If-Match", initialEtag).accept(HAL))
                .andExpect(status().isBadRequest());
        assertThat(getJson(createdLocation).path("status").asText()).isEqualTo("DRAFT");
    }

    private String doTransition(String action, String ifMatch, String expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(createdLocation + "/transitions/" + action)
                        .header("If-Match", ifMatch).accept(HAL))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(result).path("status").asText()).isEqualTo(expectedStatus);
        return result.getResponse().getHeader("ETag");
    }

    private org.springframework.test.web.servlet.MvcResult fetch(String url) throws Exception {
        return mockMvc.perform(get(url).accept(HAL)).andReturn();
    }

    private String validBody(String name) {
        return "{\"customerName\":\"" + name + "\",\"items\":["
                + "{\"sku\":\"SKU-X\",\"name\":\"Thing\",\"quantity\":2,\"unitPrice\":50}]}";
    }
}
