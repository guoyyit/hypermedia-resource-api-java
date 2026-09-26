package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Small JSON helper for integration tests. */
final class Json {

    private Json() {
    }

    static JsonNode parse(ObjectMapper mapper, String body) {
        try {
            JsonNode node = mapper.readTree(body);
            assertThat(node).isNotNull();
            return node;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON body:\n" + body, e);
        }
    }

    static String readId(String body) {
        try {
            return new ObjectMapper().readTree(body).path("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
