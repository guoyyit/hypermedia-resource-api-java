package com.github.guoyyit.hypermediaresourceapijava.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.github.guoyyit.hypermediaresourceapijava.represent.ErrorResource;
import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.represent.Representable;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.represent.ResourceList;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Deterministic JSON serialisation for all {@link Representable} types.
 *
 * <p>Two shapes are produced from one canonical model:
 * <dl>
 *   <dt>HAL ({@code hal=true})</dt>
 *   <dd>single links are objects under {@code _links}, multi-valued link
 *   relations are arrays (with a {@code name} discriminator); related
 *   resources appear under {@code _embedded} keyed by relation with array
 *   values. A resource's own properties sit at the top level.</dd>
 *   <dt>Plain ({@code hal=false})</dt>
 *   <dd>links are an ordered array under {@code links}; related resources are
 *   an ordered object under {@code embedded}; state sits under {@code state};
 *   list items sit under {@code items}; the page descriptor under
 *   {@code page}.</dd>
 * </dl>
 *
 * <p>Member order is fixed everywhere (link-relation map insertion order is
 * the documented link order), so identical state always serialises to
 * identical bytes, which keeps ETag semantics stable.
 */
@Component
public class RepresentJsonWriter {

    public void write(JsonGenerator gen, Representable value, boolean hal) throws IOException {
        if (value instanceof Resource resource) {
            writeResource(gen, resource, hal);
        } else if (value instanceof ResourceList list) {
            writeList(gen, list, hal);
        } else if (value instanceof ErrorResource error) {
            writeError(gen, error, hal);
        } else {
            gen.writeStartObject();
            gen.writeEndObject();
        }
    }

    // ------------------------------------------------------------- resource

    private void writeResource(JsonGenerator gen, Resource r, boolean hal) throws IOException {
        gen.writeStartObject();
        if (hal) {
            writeHalLinks(gen, r.getLinks());
            for (Map.Entry<String, Object> e : r.getProperties().entrySet()) {
                gen.writeObjectField(e.getKey(), e.getValue());
            }
            if (!r.getEmbedded().isEmpty()) {
                gen.writeObjectFieldStart("_embedded");
                for (Map.Entry<String, List<Resource>> e : r.getEmbedded().entrySet()) {
                    gen.writeArrayFieldStart(e.getKey());
                    for (Resource sub : e.getValue()) {
                        writeResource(gen, sub, true);
                    }
                    gen.writeEndArray();
                }
                gen.writeEndObject();
            }
        } else {
            gen.writeObjectFieldStart("state");
            for (Map.Entry<String, Object> e : r.getProperties().entrySet()) {
                gen.writeObjectField(e.getKey(), e.getValue());
            }
            gen.writeEndObject();
            writePlainLinks(gen, r.getLinks());
            if (!r.getEmbedded().isEmpty()) {
                gen.writeObjectFieldStart("embedded");
                for (Map.Entry<String, List<Resource>> e : r.getEmbedded().entrySet()) {
                    gen.writeArrayFieldStart(e.getKey());
                    for (Resource sub : e.getValue()) {
                        writeResource(gen, sub, false);
                    }
                    gen.writeEndArray();
                }
                gen.writeEndObject();
            }
        }
        gen.writeEndObject();
    }

    private void writeList(JsonGenerator gen, ResourceList list, boolean hal) throws IOException {
        gen.writeStartObject();
        if (hal) {
            writeHalLinks(gen, list.getLinks());
            gen.writeObjectFieldStart("_embedded");
            gen.writeArrayFieldStart("items");
            for (Resource item : list.getItems()) {
                writeResource(gen, item, true);
            }
            gen.writeEndArray();
            gen.writeEndObject();
            gen.writeObjectField("page", list.getPage());
        } else {
            writePlainLinks(gen, list.getLinks());
            gen.writeArrayFieldStart("items");
            for (Resource item : list.getItems()) {
                writeResource(gen, item, false);
            }
            gen.writeEndArray();
            gen.writeObjectField("page", list.getPage());
        }
        gen.writeEndObject();
    }

    private void writeError(JsonGenerator gen, ErrorResource error, boolean hal) throws IOException {
        gen.writeStartObject();
        if (hal) {
            writeHalLinks(gen, error.getLinks());
            gen.writeObjectField("error", error.getError());
        } else {
            gen.writeObjectField("error", error.getError());
            writePlainLinks(gen, error.getLinks());
        }
        gen.writeEndObject();
    }

    // ---------------------------------------------------------------- links

    private void writeHalLinks(JsonGenerator gen, Map<String, List<Link>> links) throws IOException {
        gen.writeObjectFieldStart("_links");
        for (Map.Entry<String, List<Link>> e : links.entrySet()) {
            List<Link> value = e.getValue();
            if (value.size() == 1) {
                gen.writeObjectField(e.getKey(), halLinkObject(value.get(0)));
            } else {
                gen.writeArrayFieldStart(e.getKey());
                for (Link link : value) {
                    gen.writeObject(halLinkObject(link));
                }
                gen.writeEndArray();
            }
        }
        gen.writeEndObject();
    }

    private Map<String, Object> halLinkObject(Link link) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("href", link.getHref());
        m.putAll(link.getAttributes());
        return m;
    }

    private void writePlainLinks(JsonGenerator gen, Map<String, List<Link>> links) throws IOException {
        gen.writeArrayFieldStart("links");
        for (Map.Entry<String, List<Link>> e : links.entrySet()) {
            for (Link link : e.getValue()) {
                gen.writeStartObject();
                gen.writeStringField("rel", link.getRel());
                gen.writeStringField("href", link.getHref());
                for (Map.Entry<String, String> attr : link.getAttributes().entrySet()) {
                    gen.writeStringField(attr.getKey(), attr.getValue());
                }
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
    }
}
