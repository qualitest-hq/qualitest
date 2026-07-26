package com.qualitest.flow.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResetEndpointSupportTest {

    @Test
    void resolve_plainEnvUrl_appendsTestSupportPath() {
        assertEquals(
                "http://localhost:8081/test-support",
                ResetEndpointSupport.resolve("http://localhost:8081")
        );
    }

    @Test
    void resolve_trimsTrailingSlash() {
        assertEquals(
                "http://localhost:8081/test-support",
                ResetEndpointSupport.resolve("http://localhost:8081/")
        );
    }

    @Test
    void resolve_jsonModuleUsesDefaultModule() {
        String json = "{\"默认模块\":\"http://demo:8081\",\"其他\":\"http://other:9000\"}";
        assertEquals("http://demo:8081/test-support", ResetEndpointSupport.resolve(json));
    }

    @Test
    void joinBaseAndPath_handlesEmptyBase() {
        assertEquals("", ResetEndpointSupport.joinBaseAndPath("", "/test-support"));
    }
}
