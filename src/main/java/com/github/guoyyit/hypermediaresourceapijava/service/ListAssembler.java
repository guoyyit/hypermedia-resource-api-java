package com.github.guoyyit.hypermediaresourceapijava.service;

import com.github.guoyyit.hypermediaresourceapijava.represent.Link;
import com.github.guoyyit.hypermediaresourceapijava.represent.Resource;
import com.github.guoyyit.hypermediaresourceapijava.represent.ResourceList;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds paginated collection representations with stable navigation.
 *
 * <p>Link contract:
 * <ul>
 *   <li>{@code self} — the requested page (page/size echoed, 0-based page);</li>
 *   <li>{@code first} — page 0, always present when the collection is non-empty;</li>
 *   <li>{@code last} — final page, present under the same condition;</li>
 *   <li>{@code prev} — page-1, absent on the first page;</li>
 *   <li>{@code next} — page+1, absent on the last page;</li>
 *   <li>on an empty collection ({@code total == 0}) only self/first/last point
 *       at page 0 with size in effect — a documented, distinguishable empty
 *       representation.</li>
 * </ul>
 * Out-of-range page requests (page greater than the last page index when
 * total &gt; 0) are rejected by controllers with 416 before assembly.
 *
 * <p>The {@code page} object exposes (documented order): {@code size},
 * {@code number} (0-based), {@code totalElements}, {@code totalPages}.
 * Item order is whatever stable order the caller supplies.
 */
@Component
public class ListAssembler {

    public <T> ResourceList toList(String baseHref, int page, int size, long total,
                                   List<T> items, Function<T, Resource> mapper) {
        ResourceList list = new ResourceList();

        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);

        list.getItems().addAll(items.stream().map(mapper).toList());

        Map<String, Object> p = list.getPage();
        p.put("size", size);
        p.put("number", page);
        p.put("totalElements", total);
        p.put("totalPages", totalPages);

        Map<String, List<Link>> l = list.getLinks();
        l.put("self", List.of(new Link("self", pageHref(baseHref, page, size))));
        if (total > 0) {
            l.put("first", List.of(new Link("first", pageHref(baseHref, 0, size))));
            l.put("last", List.of(new Link("last", pageHref(baseHref, totalPages - 1, size))));
            if (page > 0) {
                l.put("prev", List.of(new Link("prev", pageHref(baseHref, page - 1, size))));
            }
            if (page < totalPages - 1) {
                l.put("next", List.of(new Link("next", pageHref(baseHref, page + 1, size))));
            }
        } else {
            // Empty collection: first/last/self all describe the single empty page 0.
            l.put("first", List.of(new Link("first", pageHref(baseHref, 0, size))));
            l.put("last", List.of(new Link("last", pageHref(baseHref, 0, size))));
        }
        return list;
    }

    /** Append/replace pagination parameters without disturbing other query params. */
    private String pageHref(String baseHref, int page, int size) {
        String separator = baseHref.contains("?") ? "&" : "?";
        return baseHref + separator + "page=" + page + "&size=" + size;
    }

    /** Encode a query parameter value. */
    public static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
