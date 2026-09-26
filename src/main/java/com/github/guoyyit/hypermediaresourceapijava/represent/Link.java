package com.github.guoyyit.hypermediaresourceapijava.represent;

import java.util.Map;
import java.util.Objects;

/**
 * A hypermedia link. {@code rel} is the link relation name; {@code href} is the
 * resolved URI. Optional attributes follow HAL conventions (title, type,
 * deprecation, templated, hreflang, name, profile).
 */
public final class Link {

    private final String rel;
    private final String href;
    private final Map<String, String> attributes;

    public Link(String rel, String href) {
        this(rel, href, Map.of());
    }

    public Link(String rel, String href, Map<String, String> attributes) {
        this.rel = Objects.requireNonNull(rel, "rel");
        this.href = Objects.requireNonNull(href, "href");
        this.attributes = Map.copyOf(attributes);
    }

    public String getRel() { return rel; }
    public String getHref() { return href; }
    public Map<String, String> getAttributes() { return attributes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Link link)) return false;
        return rel.equals(link.rel) && href.equals(link.href) && attributes.equals(link.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rel, href, attributes);
    }
}
