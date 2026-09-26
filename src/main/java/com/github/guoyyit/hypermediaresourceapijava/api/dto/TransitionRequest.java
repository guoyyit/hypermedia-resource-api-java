package com.github.guoyyit.hypermediaresourceapijava.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body of POST /orders/{id}/transitions: {"action":"pay"}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransitionRequest {

    private String action;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
