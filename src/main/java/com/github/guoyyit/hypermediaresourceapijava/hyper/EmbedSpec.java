package com.github.guoyyit.hypermediaresourceapijava.hyper;

import java.util.List;

/**
 * Parsed {@code embed} and {@code fields} query parameters.
 *
 * embedPaths: ordered, de-duplicated list of dot paths, validated against
 *             the schema by the representation builder.
 * depth:       effective embedding depth (requested depth capped by config).
 * fields:      null means all fields; otherwise the canonical per-type
 *              property subset selected by the client.
 */
public class EmbedSpec {

    private List<String> embedPaths = List.of();
    private int depth = 0;
    private List<String> fields;

    public List<String> getEmbedPaths() { return embedPaths; }
    public void setEmbedPaths(List<String> embedPaths) { this.embedPaths = embedPaths; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
}
