package com.github.guoyyit.hypermediaresourceapijava.represent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single resource representation.
 * <ul>
 *   <li>{@code self} link is always present (via links keyed "self");</li>
 *   <li>{@code properties} are the resource state, deterministic insertion
 *       order (assemblers insert fields in documented order);</li>
 *   <li>{@code embedded} are related representations keyed by link relation,
 *       each value being a list with deterministic order;</li>
 *   <li>{@code links} only contains transitions legal in the current state and
 *       request context.</li>
 * </ul>
 */
public final class Resource implements Representable {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final Map<String, List<Link>> links = new LinkedHashMap<>();
    private final Map<String, List<Resource>> embedded = new LinkedHashMap<>();

    public Map<String, Object> getProperties() { return properties; }
    public Map<String, List<Link>> getLinks() { return links; }
    public Map<String, List<Resource>> getEmbedded() { return embedded; }
}
