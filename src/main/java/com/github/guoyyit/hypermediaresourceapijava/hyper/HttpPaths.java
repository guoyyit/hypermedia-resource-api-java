package com.github.guoyyit.hypermediaresourceapijava.hyper;

/** Canonical path constants shared by controllers and link builders. */
public final class HttpPaths {

    public static final String ORDERS = "/orders";

    private HttpPaths() {
    }

    public static String order(String id) {
        return ORDERS + "/" + id;
    }

    public static String transition(String id, String action) {
        return ORDERS + "/" + id + "/transitions/" + action;
    }
}
