package com.github.guoyyit.hypermediaresourceapijava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hypermedia API configuration.
 * See README for documented keys.
 */
@ConfigurationProperties(prefix = "hypermedia")
public class HypermediaProperties {

    private Pagination pagination = new Pagination();
    private Embedding embedding = new Embedding();
    private Representation representation = new Representation();
    private Data data = new Data();

    public Pagination getPagination() { return pagination; }
    public void setPagination(Pagination pagination) { this.pagination = pagination; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Representation getRepresentation() { return representation; }
    public void setRepresentation(Representation representation) { this.representation = representation; }
    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }

    public static class Pagination {
        /** Default page size when none supplied. */
        private int defaultSize = 20;
        /** Maximum allowed page size; larger values are rejected with 400. */
        private int maxSize = 100;
        /** Maximum page number accepted (1-based); 0 means unbounded. */
        private int maxPage = 0;

        public int getDefaultSize() { return defaultSize; }
        public void setDefaultSize(int defaultSize) { this.defaultSize = defaultSize; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public int getMaxPage() { return maxPage; }
        public void setMaxPage(int maxPage) { this.maxPage = maxPage; }
    }

    public static class Embedding {
        /** Maximum embedding depth applied regardless of requested depth. */
        private int maxDepth = 3;

        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    }

    public static class Representation {
        /** Media type served when the client sends no usable Accept header. */
        private String defaultMediaType = "application/hal+json";

        public String getDefaultMediaType() { return defaultMediaType; }
        public void setDefaultMediaType(String defaultMediaType) { this.defaultMediaType = defaultMediaType; }
    }

    public static class Data {
        /** Seed the in-memory store with demo orders on startup. */
        private boolean seedOnStartup = true;

        public boolean isSeedOnStartup() { return seedOnStartup; }
        public void setSeedOnStartup(boolean seedOnStartup) { this.seedOnStartup = seedOnStartup; }
    }
}
