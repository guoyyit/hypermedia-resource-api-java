package com.github.guoyyit.hypermediaresourceapijava.represent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection/listing representation with stable pagination navigation.
 * Links: self, first, last, prev, next (only present when the target page
 * exists), plus collection-level transitions such as "order-create".
 */
public final class ResourceList implements Representable {

    private final Map<String, List<Link>> links = new LinkedHashMap<>();
    private final List<Resource> items = new ArrayList<>();
    private final Map<String, Object> page = new LinkedHashMap<>();

    public Map<String, List<Link>> getLinks() { return links; }
    public List<Resource> getItems() { return items; }
    public Map<String, Object> getPage() { return page; }
}
