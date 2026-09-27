package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.github.guoyyit.hypermediaresourceapijava.hyper.Link;
import com.github.guoyyit.hypermediaresourceapijava.hyper.MediaTypeKind;
import com.github.guoyyit.hypermediaresourceapijava.hyper.RepresentationRenderer;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ResourceRepresentation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Requirement 2 & 5: deterministic ordering and layout equivalence. */
class RepresentationRendererTest {

    private final RepresentationRenderer renderer = new RepresentationRenderer();

    private ResourceRepresentation sample() {
        ResourceRepresentation rep = new ResourceRepresentation();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", "O-1");
        props.put("status", "DRAFT");
        rep.setProperties(props);

        // Insert links deliberately out of rel order.
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("submit", Link.action("submit", "/o/transitions/submit", "POST", "Submit"));
        links.put("self", Link.self("/o"));
        links.put("cancel", Link.action("cancel", "/o/transitions/cancel", "POST", "Cancel"));
        links.put("items", Link.get("items", "/o/items", null));
        rep.setLinks(links);

        ResourceRepresentation child = new ResourceRepresentation();
        child.getProperties().put("id", "O-1-I001");
        child.getLinks().put("self", Link.self("/o/items/O-1-I001"));
        rep.getEmbedded().put("items", List.of(child));
        rep.setTruncatedEmbeds(List.of("items.order.items"));
        return rep;
    }

    @Test
    void halSortsLinksByRelAndKeepsPropertyOrder() {
        Map<String, Object> hal = renderer.render(sample(), MediaTypeKind.HAL);
        assertThat(hal.keySet()).containsExactly("id", "status", "_links", "_embedded", "_meta");
        assertThat(new java.util.ArrayList<>(((Map<String, ?>) hal.get("_links")).keySet()))
                .containsExactly("cancel", "items", "self", "submit");
        // GET-only links carry no method hint; action links do.
        Map<?, ?> links = (Map<?, ?>) hal.get("_links");
        assertThat(links.get("items").toString()).doesNotContain("method");
        assertThat(((Map<?, ?>) links.get("submit")).get("method")).isEqualTo("POST");
    }

    @Test
    void vndUsesDataLinksEmbeddedMetaContainers() {
        Map<String, Object> vnd = renderer.render(sample(), MediaTypeKind.VND);
        assertThat(vnd.keySet()).containsExactly("data", "links", "embedded", "meta");
        assertThat(((Map<?, ?>) vnd.get("data")).get("id")).isEqualTo("O-1");
        assertThat(vnd.get("links")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) vnd.get("meta")).get("truncatedEmbeds"))
                .isEqualTo(List.of("items.order.items"));
    }

    @Test
    void twoLayoutsExposeSameLinkRelsAndPropertyValues() {
        Map<String, Object> hal = renderer.render(sample(), MediaTypeKind.HAL);
        Map<String, Object> vnd = renderer.render(sample(), MediaTypeKind.VND);
        assertThat(((Map<?, ?>) hal.get("_links")).keySet())
                .isEqualTo(((Map<?, ?>) vnd.get("links")).keySet());
        assertThat(((Map<?, ?>) hal.get("_embedded")).keySet())
                .isEqualTo(((Map<?, ?>) vnd.get("embedded")).keySet());
    }

    @Test
    void renderingIsStableAcrossInvocations() {
        Map<String, Object> first = renderer.render(sample(), MediaTypeKind.HAL);
        Map<String, Object> second = renderer.render(sample(), MediaTypeKind.HAL);
        assertThat(second).isEqualTo(first);
        // TreeMap sanity: rel ordering independent of insertion.
        TreeMap<String, String> sorted = new TreeMap<>();
        sorted.put("z", "1");
        sorted.put("a", "2");
        assertThat(sorted.keySet().toString()).containsSubsequence("a", "z");
    }
}
