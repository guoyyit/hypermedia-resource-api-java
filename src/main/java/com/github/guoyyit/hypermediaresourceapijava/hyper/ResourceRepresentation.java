package com.github.guoyyit.hypermediaresourceapijava.hyper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Media-type-neutral hypermedia representation.
 *
 * Property, link and embedded-resource maps are all {@link LinkedHashMap}s
 * so the emitted field order is deterministic:
 * properties in canonical field order, links sorted by rel, embedded sorted
 * by relation name.
 */
public class ResourceRepresentation {

    private String selfHref;
    private Map<String, Object> properties = new LinkedHashMap<>();
    private Map<String, Link> links = new LinkedHashMap<>();
    private Map<String, Object> embedded = new LinkedHashMap<>();
    private List<String> truncatedEmbeds = List.of();

    public String getSelfHref() { return selfHref; }
    public void setSelfHref(String selfHref) { this.selfHref = selfHref; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public Map<String, Link> getLinks() { return links; }
    public void setLinks(Map<String, Link> links) { this.links = links; }
    public Map<String, Object> getEmbedded() { return embedded; }
    public void setEmbedded(Map<String, Object> embedded) { this.embedded = embedded; }
    public List<String> getTruncatedEmbeds() { return truncatedEmbeds; }
    public void setTruncatedEmbeds(List<String> truncatedEmbeds) { this.truncatedEmbeds = truncatedEmbeds; }
}
