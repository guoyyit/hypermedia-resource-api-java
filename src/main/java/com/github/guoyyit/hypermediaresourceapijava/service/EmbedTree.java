package com.github.guoyyit.hypermediaresourceapijava.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered, de-duplicated tree of embed paths requested by the client.
 *
 * <p>A request such as {@code ?embed=customer,items.product,customer.orders}
 * produces:
 * <pre>
 *   root
 *   ├── customer
 *   │   └── orders
 *   └── items
 *       └── product
 * </pre>
 *
 * <p>Documented semantics:
 * <ul>
 *   <li>paths are split on '.', blank segments ignored; unknown relations are
 *       dropped silently (they never cause an error, per documented
 *       "best-effort embedding" behaviour);</li>
 *   <li>sibling order equals first-occurrence order in the query string;</li>
 *   <li>{@code maxDepth} (bounded by {@code hypermedia.embed-max-depth}) counts
 *       edges from the root resource: only nodes with depth &lt; maxDepth are
 *       expanded; deeper nodes are truncated deterministically;</li>
 *   <li>the assembler additionally tracks a set of visited resource ids on
 *       each expansion chain so cycles become reference links instead of
 *       infinite recursion.</li>
 * </ul>
 */
public final class EmbedTree {

    public static final class Node {
        private final String name;
        private final int depth;
        private final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name, int depth) {
            this.name = name;
            this.depth = depth;
        }

        public String getName() { return name; }
        public int getDepth() { return depth; }
        public Map<String, Node> getChildren() { return children; }
    }

    private final Node root = new Node("", 0);
    private int truncatedPaths = 0;

    private EmbedTree() {
    }

    public Node getRoot() { return root; }
    public int getTruncatedPaths() { return truncatedPaths; }

    public boolean isEmpty() {
        return root.getChildren().isEmpty();
    }

    /** Parse an ordered list of embed paths with an explicit depth budget. */
    public static EmbedTree parse(List<String> paths, int maxDepth) {
        EmbedTree tree = new EmbedTree();
        if (paths != null) {
            for (String path : paths) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                Node current = tree.root;
                int depth = 0;
                for (String segment : path.trim().split("\\.")) {
                    if (segment.isBlank()) {
                        continue;
                    }
                    depth++;
                    if (depth > maxDepth) {
                        tree.truncatedPaths++;
                        break;
                    }
                    Node next = current.getChildren().get(segment);
                    if (next == null) {
                        next = new Node(segment, depth);
                        current.getChildren().put(segment, next);
                    }
                    current = next;
                }
            }
        }
        return tree;
    }

    /** Child relations enabled directly under the root, in request order. */
    public List<String> rootRelations() {
        return new ArrayList<>(root.getChildren().keySet());
    }
}
