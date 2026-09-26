package com.github.guoyyit.hypermediaresourceapijava.domain;

/** Immutable catalog product. */
public class Product {

    private final String id;
    private final String name;
    private final long price;

    public Product(String id, String name, long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public long getPrice() { return price; }
}
