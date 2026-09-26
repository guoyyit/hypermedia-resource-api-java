package com.github.guoyyit.hypermediaresourceapijava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hypermedia configuration, prefix {@code hypermedia}.
 *
 * @param embedMaxDepth   maximum depth for {@code embed} expansion (default 3)
 * @param defaultPageSize default page size when {@code size} is absent
 * @param maxPageSize     upper bound accepted for {@code size}
 * @param basePath        external API base used for absolute link hrefs
 */
@ConfigurationProperties(prefix = "hypermedia")
public class HypermediaProperties {

    private int embedMaxDepth = 3;
    private int defaultPageSize = 5;
    private int maxPageSize = 20;
    private String basePath = "";

    public int getEmbedMaxDepth() { return embedMaxDepth; }
    public void setEmbedMaxDepth(int embedMaxDepth) { this.embedMaxDepth = embedMaxDepth; }

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }

    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
}
