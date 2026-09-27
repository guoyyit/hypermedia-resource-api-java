package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.domain.Order;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderItem;
import com.github.guoyyit.hypermediaresourceapijava.domain.OrderStatus;
import com.github.guoyyit.hypermediaresourceapijava.hyper.EmbedSpec;
import com.github.guoyyit.hypermediaresourceapijava.hyper.Link;
import com.github.guoyyit.hypermediaresourceapijava.hyper.ResourceRepresentation;
import com.github.guoyyit.hypermediaresourceapijava.store.InMemoryOrderStore;
import com.github.guoyyit.hypermediaresourceapijava.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds hypermedia representations.
 *
 * Determinism / embedding contract (see README):
 * <ul>
 *   <li>properties are emitted in a fixed canonical order;</li>
 *   <li>links are sorted by rel, and transition controls exist only when the
 *       action is legal in the order's current status;</li>
 *   <li>embedded relations are sorted by name; item lists are ordered by
 *       item id;</li>
 *   <li>embedding is depth-bounded (requested depth capped by config) and
 *       cycle-safe: a node (kind:id) already on the current expansion chain
 *       is replaced by a link and the path is reported in
 *       {@code truncatedEmbeds}, guaranteeing termination on
 *       item -&gt; order -&gt; items cycles;</li>
 *   <li>relations cut off by the configured depth cap are likewise reported
 *       in {@code truncatedEmbeds}, in the order first encountered.</li>
 * </ul>
 */
@Service
public class OrderRepresentationService {

    static final List<String> ORDER_FIELDS = List.of(
            "id", "status", "customerName", "totalPrice", "itemCount", "updatedAt");
    static final List<String> ITEM_FIELDS = List.of(
            "id", "sku", "name", "quantity", "unitPrice");

    private static final Set<String> ORDER_SORT_KEYS =
            new TreeSet<>(List.of("id", "customerName", "status", "updatedAt"));

    private final InMemoryOrderStore store;

    public OrderRepresentationService(InMemoryOrderStore store) {
        this.store = store;
    }

    // ------------------------------------------------------------------ parse

    public EmbedSpec parseEmbedSpec(String embed, String depth, String fields, int configuredMaxDepth) {
        EmbedSpec spec = new EmbedSpec();

        int requestedDepth = 1;
        if (depth != null && !depth.isBlank()) {
            try {
                requestedDepth = Integer.parseInt(depth.trim());
            } catch (NumberFormatException e) {
                throw badEmbed("depth must be a non-negative integer");
            }
            if (requestedDepth < 0) {
                throw badEmbed("depth must be a non-negative integer");
            }
        }
        spec.setDepth(Math.min(requestedDepth, Math.max(0, configuredMaxDepth)));

        List<String> paths = new ArrayList<>();
        if (embed != null && !embed.isBlank()) {
            for (String raw : embed.split(",")) {
                String path = raw.trim();
                if (!path.isEmpty()) {
                    paths.add(path);
                }
            }
        }
        spec.setEmbedPaths(new ArrayList<>(new LinkedHashSet<>(paths)));
        validatePaths(spec.getEmbedPaths());

        if (fields != null && !fields.isBlank()) {
            List<String> distinct = new ArrayList<>();
            for (String raw : fields.split(",")) {
                String field = raw.trim();
                if (!field.isEmpty() && !distinct.contains(field)) {
                    distinct.add(field);
                }
            }
            for (String field : distinct) {
                if (!ORDER_FIELDS.contains(field) && !ITEM_FIELDS.contains(field)) {
                    throw badEmbed("Unknown field selector: " + field);
                }
            }
            spec.setFields(distinct);
        }
        return spec;
    }

    private void validatePaths(List<String> paths) {
        // Schema on an order root: items(.order.items...)*
        for (String path : paths) {
            String[] segs = path.split("\\.");
            String expect = "order";
            for (String seg : segs) {
                switch (seg) {
                    case "items" -> {
                        if (!expect.equals("order")) {
                            throw badEmbed("Invalid embed path: " + path);
                        }
                        expect = "item";
                    }
                    case "order" -> {
                        if (!expect.equals("item")) {
                            throw badEmbed("Invalid embed path: " + path);
                        }
                        expect = "order";
                    }
                    default -> throw badEmbed("Unknown embed relation '" + seg + "' in path: " + path);
                }
            }
        }
    }

    private static BadRequestException badEmbed(String message) {
        return new BadRequestException("INVALID_EMBED", message,
                Map.of("self", "/orders", "orders", "/orders"));
    }

    // ------------------------------------------------------------- entries

    public ResourceRepresentation orderRepresentation(Order order, EmbedSpec spec) {
        BuildContext ctx = new BuildContext(spec);
        ResourceRepresentation rep = buildOrder(order, ctx, 0, "", spec.getEmbedPaths());
        rep.setTruncatedEmbeds(new ArrayList<>(ctx.truncated));
        return rep;
    }

    public ResourceRepresentation itemRepresentation(OrderItem item, EmbedSpec spec) {
        BuildContext ctx = new BuildContext(spec);
        ResourceRepresentation rep = buildItem(item, ctx, 0, "",
                shiftForItemRoot(spec.getEmbedPaths()),
                itemSelfHref(item.getOrder().getId(), item.getId()), true);
        rep.setTruncatedEmbeds(new ArrayList<>(ctx.truncated));
        return rep;
    }

    public ResourceRepresentation itemCollectionRepresentation(Order order,
                                                               String baseHref,
                                                               EmbedSpec spec) {
        BuildContext ctx = new BuildContext(spec);
        ResourceRepresentation rep = new ResourceRepresentation();
        String self = orderItemsHref(order.getId());
        rep.setSelfHref(self);
        rep.getProperties().put("id", order.getId() + ":items");
        rep.getProperties().put("count", order.getItems().size());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.self(self));
        links.put("order", Link.get("order", orderSelfHref(order.getId()), "Owning order"));
        rep.setLinks(new LinkedHashMap<>(new TreeMap<>(links)));

        List<ResourceRepresentation> members = new ArrayList<>();
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(item -> members.add(buildItem(item, ctx, 0, "",
                        shiftForItemRoot(spec.getEmbedPaths()),
                        itemSelfHref(order.getId(), item.getId()), false)));
        if (!members.isEmpty()) {
            rep.getEmbedded().put("items", members);
        }
        rep.setTruncatedEmbeds(new ArrayList<>(ctx.truncated));
        return rep;
    }

    public ResourceRepresentation orderCollectionRepresentation(List<Order> allOrders,
                                                                int page, int size,
                                                                String sort,
                                                                String baseHref,
                                                                String queryPrefix,
                                                                EmbedSpec spec) {
        List<Order> sorted = sortOrders(allOrders, sort);
        int total = sorted.size();
        int lastPage = Math.max(1, (int) Math.ceil(total / (double) size));

        ResourceRepresentation rep = new ResourceRepresentation();
        String selfHref = pageHref(baseHref, page, size, sort);
        rep.setSelfHref(selfHref);

        Map<String, Object> pageProps = new LinkedHashMap<>();
        pageProps.put("page", page);
        pageProps.put("size", size);
        pageProps.put("totalItems", total);
        rep.setProperties(pageProps);

        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.self(selfHref));
        links.put("first", Link.get("first", pageHref(baseHref, 1, size, sort), "First page"));
        links.put("last", Link.get("last", pageHref(baseHref, lastPage, size, sort), "Last page"));
        if (page > 1) {
            links.put("prev", Link.get("prev", pageHref(baseHref, page - 1, size, sort), "Previous page"));
        }
        if (page < lastPage) {
            links.put("next", Link.get("next", pageHref(baseHref, page + 1, size, sort), "Next page"));
        }
        links.put("create", Link.action("create", "/orders", "POST", "Create a new order"));
        // Safe navigation only for out-of-range pages.
        if (page > lastPage) {
            links.remove("prev");
            links.remove("next");
        }
        rep.setLinks(new LinkedHashMap<>(new TreeMap<>(links)));

        List<ResourceRepresentation> members = new ArrayList<>();
        boolean outOfRange = page > lastPage;
        if (!outOfRange && total > 0) {
            long from = (long) (page - 1) * size;
            long to = Math.min(from + size, total);
            BuildContext ctx = new BuildContext(spec);
            for (long i = from; i < to; i++) {
                Order order = sorted.get((int) i);
                // Each collection member gets an independent cycle chain rooted at itself.
                BuildContext memberCtx = new BuildContext(spec);
                ResourceRepresentation mr = buildOrder(order, memberCtx, 0, "", spec.getEmbedPaths());
                for (String t : memberCtx.truncated) {
                    if (!ctx.truncated.contains(t)) {
                        ctx.truncated.add(t);
                    }
                }
                members.add(mr);
            }
            rep.setTruncatedEmbeds(new ArrayList<>(ctx.truncated));
        } else {
            rep.setTruncatedEmbeds(List.of());
        }
        rep.getEmbedded().put("orders", members);
        return rep;
    }

    /**
     * Stable comparator. Unknown sort keys are a 400. Every comparator breaks
     * ties by order id so equal keys never reorder between requests.
     */
    public Comparator<Order> sortComparator(String sort) {
        String key = sort == null || sort.isBlank() ? "id" : sort.trim();
        boolean descending = key.startsWith("-");
        if (descending) {
            key = key.substring(1);
        }
        if (!ORDER_SORT_KEYS.contains(key)) {
            throw new BadRequestException(
                    "INVALID_SORT",
                    "Unknown sort key '" + key + "'; allowed: id, customerName, status, updatedAt",
                    Map.of("self", "/orders", "orders", "/orders"));
        }
        Comparator<Order> base = switch (key) {
            case "customerName" -> Comparator.comparing(Order::getCustomerName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(o -> o.getStatus().name());
            case "updatedAt" -> Comparator.comparing(Order::getUpdatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(Order::getId);
        };
        base = base.thenComparing(Order::getId);
        return descending ? base.reversed() : base;
    }

    private List<Order> sortOrders(List<Order> orders, String sort) {
        List<Order> copy = new ArrayList<>(orders);
        copy.sort(sortComparator(sort));
        return copy;
    }

    // ------------------------------------------------------------- builders

    private static final class BuildContext {
        final EmbedSpec spec;
        /** Nodes currently open on the expansion chain (cycle back-edge guard). */
        final Set<String> chain = new LinkedHashSet<>();
        /**
         * Nodes whose outgoing relations have already been expanded once.
         * A second occurrence is rendered with links only. This is what makes
         * item -> order -> item cycles terminate while still allowing the
         * first order -> item -> order back-edge to embed the owning order.
         */
        final Set<String> sealed = new LinkedHashSet<>();
        /** Deterministic truncation notices. */
        final Set<String> truncated = new LinkedHashSet<>();

        BuildContext(EmbedSpec spec) {
            this.spec = spec;
        }
    }

    private ResourceRepresentation buildOrder(Order order, BuildContext ctx, int depth,
                                              String path, List<String> remainingPrefixes) {
        return buildOrder(order, ctx, depth, path, remainingPrefixes,
                orderSelfHref(order.getId()), true);
    }

    private ResourceRepresentation buildOrder(Order order, BuildContext ctx, int depth,
                                              String path, List<String> remainingPrefixes,
                                              String selfHref,
                                              boolean depthDriven) {
        String nodeKey = "order:" + order.getId();
        ResourceRepresentation rep = new ResourceRepresentation();
        rep.setSelfHref(selfHref);
        putOrderProperties(rep, order, ctx.spec);

        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.self(selfHref));
        links.put("items", Link.get("items", orderItemsHref(order.getId()), "Line items"));
        links.put("orders", Link.get("orders", "/orders", "All orders"));
        if (order.getStatus() == OrderStatus.DRAFT) {
            links.put("edit", Link.action("edit", selfHref, "PUT", "Update draft order"));
        }
        for (String action : List.of("submit", "pay", "ship", "deliver", "cancel")) {
            if (order.getStatus().after(action).isPresent()) {
                links.put(action, Link.action(action,
                        selfHref + "/transitions/" + action, "POST",
                        order.getStatus().actionTitle(action)));
            }
        }
        rep.setLinks(new LinkedHashMap<>(new TreeMap<>(links)));

        boolean root = path == null || path.isEmpty();
        boolean canExpandOutgoing = root || !ctx.sealed.contains(nodeKey);
        boolean itemsByDepth = depth < ctx.spec.getDepth();
        boolean itemsByPath = hasPrefix(remainingPrefixes, "items");
        boolean wantItems = canExpandOutgoing && (itemsByDepth || itemsByPath);
        if (wantItems) {
            String childPath = path.isEmpty() ? "items" : path + ".items";
            if (ctx.chain.contains(nodeKey)) {
                // Re-entering the same order node via a cycle: links only.
                ctx.truncated.add(childPath);
            } else {
                ctx.chain.add(nodeKey);
                List<String> childPrefixes = advance(remainingPrefixes, "items");
                // Whether this hop exists purely because of depth auto-expansion
                // (no explicit path covers it). Only such chains are reported
                // when the depth cap cuts them off.
                boolean childDepthDriven = depthDriven && !itemsByPath;
                List<ResourceRepresentation> members = new ArrayList<>();
                for (OrderItem item : order.getItems().stream()
                        .sorted(Comparator.comparing(OrderItem::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList()) {
                    members.add(buildItem(item, ctx, depth + 1, childPath, childPrefixes,
                            itemSelfHref(order.getId(), item.getId()), childDepthDriven));
                }
                ctx.chain.remove(nodeKey);
                if (!root) {
                    ctx.sealed.add(nodeKey);
                }
                if (!members.isEmpty()) {
                    rep.getEmbedded().put("items", members);
                }
            }
        } else if (depthDriven && !root && !itemsByDepth) {
            // Auto-expansion was cut off by the configured depth cap: report it.
            ctx.truncated.add(path + ".items");
        }
        return rep;
    }

    private ResourceRepresentation buildItem(OrderItem item, BuildContext ctx, int depth,
                                             String path, List<String> remainingPrefixes,
                                             String selfHref, boolean depthDriven) {
        String nodeKey = "item:" + item.getId();
        ResourceRepresentation rep = new ResourceRepresentation();
        rep.setSelfHref(selfHref);
        putItemProperties(rep, item, ctx.spec);

        String orderId = item.getOrder().getId();
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", Link.self(selfHref));
        links.put("order", Link.get("order", orderSelfHref(orderId), "Owning order"));
        links.put("items", Link.get("items", orderItemsHref(orderId), "All line items"));
        rep.setLinks(new LinkedHashMap<>(new TreeMap<>(links)));

        boolean orderByDepth = depth < ctx.spec.getDepth();
        boolean orderByPath = hasPrefix(remainingPrefixes, "order");
        boolean wantOrder = orderByDepth || orderByPath;
        if (wantOrder) {
            String childPath = path.isEmpty() ? "order" : path + ".order";
            if (ctx.chain.contains(nodeKey)) {
                ctx.truncated.add(childPath);
            } else {
                ctx.chain.add(nodeKey);
                List<String> childPrefixes = advance(remainingPrefixes, "order");
                boolean childDepthDriven = depthDriven && !orderByPath;
                rep.getEmbedded().put("order",
                        buildOrder(item.getOrder(), ctx, depth + 1, childPath, childPrefixes,
                                orderSelfHref(orderId), childDepthDriven));
                ctx.chain.remove(nodeKey);
                ctx.sealed.add(nodeKey);
            }
        }
        return rep;
    }

    // -------------------------------------------------------- path matching

    /** True if any remaining explicit path starts with {@code seg}. */
    private boolean hasPrefix(List<String> prefixes, String seg) {
        for (String p : prefixes) {
            if (p.equals(seg) || p.startsWith(seg + ".")) {
                return true;
            }
        }
        return false;
    }

    private boolean explicitlyRequested(List<String> prefixes, String seg) {
        return prefixes.contains(seg);
    }

    /** Strip {@code seg} from the front of matching prefixes for the child. */
    private List<String> advance(List<String> prefixes, String seg) {
        List<String> next = new ArrayList<>();
        for (String p : prefixes) {
            if (p.equals(seg)) {
                next.add("");
            } else if (p.startsWith(seg + ".")) {
                next.add(p.substring(seg.length() + 1));
            }
        }
        return next;
    }

    /**
     * At an item root, paths supplied for an order root (items.order...) must
     * be reinterpreted: the first 'items.' is implicit because the root is
     * already an item. We only consume paths that actually start that way;
     * 'items' alone is irrelevant on an item root.
     */
    private List<String> shiftForItemRoot(List<String> orderRootPaths) {
        List<String> shifted = new ArrayList<>();
        for (String p : orderRootPaths) {
            if (p.startsWith("items.")) {
                shifted.add(p.substring("items.".length()));
            }
        }
        return shifted;
    }

    // --------------------------------------------------------- property view

    private void putOrderProperties(ResourceRepresentation rep, Order order, EmbedSpec spec) {
        long total = order.getItems().stream()
                .mapToLong(i -> (long) i.getQuantity() * i.getUnitPrice()).sum();
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("id", order.getId());
        all.put("status", order.getStatus().name());
        all.put("customerName", order.getCustomerName());
        all.put("totalPrice", total);
        all.put("itemCount", order.getItems().size());
        all.put("updatedAt", order.getUpdatedAt() == null ? null : order.getUpdatedAt().toString());
        project(rep, all, ORDER_FIELDS, spec);
    }

    private void putItemProperties(ResourceRepresentation rep, OrderItem item, EmbedSpec spec) {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("id", item.getId());
        all.put("sku", item.getSku());
        all.put("name", item.getName());
        all.put("quantity", item.getQuantity());
        all.put("unitPrice", item.getUnitPrice());
        project(rep, all, ITEM_FIELDS, spec);
    }

    private void project(ResourceRepresentation rep, Map<String, Object> all,
                         List<String> canonical, EmbedSpec spec) {
        List<String> wanted = spec.getFields();
        if (wanted == null) {
            rep.getProperties().putAll(all);
            return;
        }
        for (String field : canonical) {
            if (wanted.contains(field)) {
                rep.getProperties().put(field, all.get(field));
            }
        }
    }

    // ---------------------------------------------------------------- hrefs

    public static String orderSelfHref(String id) {
        return "/orders/" + id;
    }

    public static String orderItemsHref(String id) {
        return "/orders/" + id + "/items";
    }

    public static String itemSelfHref(String orderId, String itemId) {
        return "/orders/" + orderId + "/items/" + itemId;
    }

    private String pageHref(String baseHref, int page, int size, String sort) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(baseHref)
                .queryParam("page", page)
                .queryParam("size", size);
        if (sort != null && !sort.isBlank()) {
            b.queryParam("sort", sort);
        }
        return b.build().toUriString();
    }
}
