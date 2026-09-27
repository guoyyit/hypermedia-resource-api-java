package com.github.guoyyit.hypermediaresourceapijava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Shared MockMvc + JSON helpers. The seeded store provides O-1001..O-1006
 * in every status; tests that mutate state create fresh orders instead of
 * depending on a particular seed version.
 */
public abstract class AbstractApiTest {

    protected static final MediaType HAL = MediaType.parseMediaType("application/hal+json");
    protected static final MediaType VND = MediaType.parseMediaType("application/vnd.hypermedia+json");

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected ObjectMapper mapper;

    protected MockMvc mockMvc;

    protected void initMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    protected String etag(MvcResult result) {
        return result.getResponse().getHeader("ETag");
    }

    protected JsonNode getJson(String url) throws Exception {
        return json(mockMvc.perform(get(url).accept(HAL)).andReturn());
    }
}
