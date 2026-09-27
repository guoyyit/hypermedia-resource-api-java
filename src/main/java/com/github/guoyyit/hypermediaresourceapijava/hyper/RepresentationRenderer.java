package com.github.guoyyit.hypermediaresourceapijava.hyper;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a {@link ResourceRepresentation} to a wire map.
 *
 * Deterministic layout:
 * <ul>
 *   <li>properties keep the canonical insertion order of the representation;</li>
 *   <li>link objects are emitted sorted by rel;</li>
 *   <li>embedded resources are emitted sorted by relation name, and list
 *       members preserve their (already sorted) input order;</li>
 *   <li>absent method/title parts are omitted rather than emitted as null.</li>
 * </ul>
 *
 * Two equivalent shapes exist:
 * HAL  ({@code application/hal+json}): properties at top level plus
 * {@code _links}/{@code _embedded}/{@code _meta}.
 * VND  ({@code application/vnd.hypermedia+json}): {@code data},
 * {@code links}, {@code embedded}, {@code meta}.
 * The two shapes never differ in semantics or resource state.
 */
@Component
public class RepresentationRenderer {

    public Map<String, Object> render(ResourceRepresentation rep, MediaTypeKind kind) {
        return kind == MediaTypeKind.VND ? renderVnd(rep) : renderHal(rep);
    }

    private Map<String, Object> renderHal(ResourceRepresentation rep) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(rep.getProperties());
        out.put("_links", linksBlock(rep));
        if (!rep.getEmbedded().isEmpty()) {
            out.put("_embedded", embeddedBlock(rep, MediaTypeKind.HAL));
        }
        if (!rep.getTruncatedEmbeds().isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("truncatedEmbeds", new ArrayList<>(rep.getTruncatedEmbeds()));
            out.put("_meta", meta);
        }
        return out;
    }

    private Map<String, Object> renderVnd(ResourceRepresentation rep) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", new LinkedHashMap<>(rep.getProperties()));
        out.put("links", linksBlock(rep));
        if (!rep.getEmbedded().isEmpty()) {
            out.put("embedded", embeddedBlock(rep, MediaTypeKind.VND));
        }
        if (!rep.getTruncatedEmbeds().isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("truncatedEmbeds", new ArrayList<>(rep.getTruncatedEmbeds()));
            out.put("meta", meta);
        }
        return out;
    }

    private Map<String, Object> linksBlock(ResourceRepresentation rep) {
        Map<String, Object> block = new TreeMap<>();
        rep.getLinks().forEach((rel, link) -> block.put(rel, linkObject(link)));
        return block;
    }

    private Map<String, Object> linkObject(Link link) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("href", link.href());
        if (link.href() != null && link.href().contains("{")) {
            obj.put("templated", true);
        }
        if (link.method() != null && !link.method().isBlank()) {
            obj.put("method", link.method());
        }
        if (link.title() != null && !link.title().isBlank()) {
            obj.put("title", link.title());
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> embeddedBlock(ResourceRepresentation rep, MediaTypeKind kind) {
        Map<String, Object> block = new TreeMap<>();
        new TreeMap<>(rep.getEmbedded()).forEach((name, value) -> {
            if (value instanceof ResourceRepresentation child) {
                block.put(name, render(child, kind));
            } else if (value instanceof List<?> list) {
                List<Object> rendered = new ArrayList<>();
                for (Object member : list) {
                    if (member instanceof ResourceRepresentation child) {
                        rendered.add(render(child, kind));
                    } else {
                        rendered.add(member);
                    }
                }
                block.put(name, rendered);
            } else {
                block.put(name, value);
            }
        });
        return block;
    }
}
