package com.github.guoyyit.hypermediaresourceapijava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body of POST/PUT /customers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerRequest {

    private String name;
    private String email;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
