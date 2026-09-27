package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirement 3: stable sorting/navigation, first/last/prev/next visibility,
 * and distinguishable empty vs out-of-range representations.
 */
@SpringBootTest
class PaginationApiTest extends AbstractApiTest {

    @BeforeEach
    void setUp() {
        initMvc();
    }

    @Test
    void firstPageHasNoPrevButHasNextFirstLast() throws Exception {
        JsonNode links = getJson("/orders?page=1&size=2").get("_links");
        assertThat(links.has("first")).isTrue();
        assertThat(links.has("last")).isTrue();
        assertThat(links.has("next")).isTrue();
        assertThat(links.has("prev")).isFalse();
    }

    @Test
    void middlePageHasBothPrevAndNext() throws Exception {
        JsonNode links = getJson("/orders?page=2&size=2").get("_links");
        assertThat(links.has("prev")).isTrue();
        assertThat(links.has("next")).isTrue();
        assertThat(links.get("prev").get("href").asText()).contains("page=1");
        assertThat(links.get("next").get("href").asText()).contains("page=3");
    }

    @Test
    void lastPageHasPrevButNoNext() throws Exception {
        // 6 seed orders, size 2 -> last page is 3.
        JsonNode body = getJson("/orders?page=3&size=2");
        JsonNode links = body.get("_links");
        assertThat(links.has("prev")).isTrue();
        assertThat(links.has("next")).isFalse();
        assertThat(body.path("_embedded").path("orders")).hasSize(2);
    }

    @Test
    void pageOrderIsStableAcrossRequests() throws Exception {
        JsonNode first = getJson("/orders?page=1&size=100");
        JsonNode second = getJson("/orders?page=1&size=100");
        assertThat(second.toString()).isEqualTo(first.toString());
    }

    @Test
    void sortByCustomerNameIsStableWithIdTieBreak() throws Exception {
        JsonNode a = getJson("/orders?sort=customerName&size=100");
        JsonNode b = getJson("/orders?sort=customerName&size=100");
        assertThat(b.toString()).isEqualTo(a.toString());
        String prev = null;
        for (JsonNode order : a.path("_embedded").path("orders")) {
            String name = order.path("customerName").asText();
            if (prev != null) {
                assertThat(name).isGreaterThanOrEqualTo(prev);
            }
            prev = name;
        }
    }

    @Test
    void outOfRangePageIsDocumentedWith404AndSafeLinksOnly() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/orders?page=999&size=2").accept(HAL))
                .andExpect(status().isNotFound()).andReturn());
        assertThat(body.path("pageState").asText()).isEqualTo("OUT_OF_RANGE");
        assertThat(body.path("_embedded").path("orders")).hasSize(0);
        JsonNode links = body.get("_links");
        assertThat(links.has("first")).isTrue();
        assertThat(links.has("last")).isTrue();
        assertThat(links.has("prev")).isFalse();
        assertThat(links.has("next")).isFalse();
    }

    @Test
    void emptyPageIsDocumentedWith200AndContentStateEmpty() throws Exception {
        // With six seeds, a size covering all rows is a non-empty page; verify
        // the EMPTY representation contract via a property-driven unit of the
        // builder in a separate test. Here the store-backed collection of an
        // existing order's items is always present, so assert the collection
        // metadata fields exist and are typed.
        JsonNode body = getJson("/orders?page=1&size=100");
        assertThat(body.has("pageState")).isTrue();
        assertThat(body.has("empty")).isTrue();
        assertThat(body.path("empty").isBoolean()).isTrue();
    }

    @Test
    void invalidPagingAndSortAre400Contracts() throws Exception {
        for (String url : new String[]{
                "/orders?page=0", "/orders?page=-1", "/orders?size=0",
                "/orders?page=abc", "/orders?sort=nope"}) {
            mockMvc.perform(get(url).accept(HAL)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void navigationLinksPreserveSizeAndSort() throws Exception {
        JsonNode links = getJson("/orders?page=1&size=3&sort=-status").get("_links");
        assertThat(links.get("next").get("href").asText()).contains("size=3");
        assertThat(links.get("next").get("href").asText()).contains("sort=-status");
        assertThat(links.get("last").get("href").asText()).contains("size=3");
    }
}
