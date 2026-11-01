package com.szr.crosserp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

final class JsonTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestSupport() {
    }

    static String string(MvcResult result, String pointer) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(result.getResponse().getContentAsByteArray());
        return root.at(pointer).asText();
    }
}
