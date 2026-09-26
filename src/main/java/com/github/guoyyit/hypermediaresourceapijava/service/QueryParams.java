package com.github.guoyyit.hypermediaresourceapijava.service;

import java.util.List;

/**
 * Parsed list/embed query parameters shared by controllers and assemblers.
 *
 * @param embed      ordered, de-duplicated embed paths (e.g. ["customer", "items.product"])
 * @param embedDepth maximum expansion depth allowed for this request
 * @param fields     nullable ordered, de-duplicated property field selection
 * @param page       zero-based page index
 * @param size       page size
 */
public record QueryParams(List<String> embed, int embedDepth, List<String> fields,
                          int page, int size) {
}
