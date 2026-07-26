package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.ApiConfigV2TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlowDesignApiSummarizer} 单元测试。
 */
class FlowDesignApiSummarizerTest {

    @Test
    void summarizeRequest_readsQueryParamsAndObjectHeaders() {
        var out = FlowDesignApiSummarizer.summarizeRequest(
                ApiConfigV2TestFixtures.REQUEST_POST_WITH_QUERY,
                "{\"Authorization\":\"Bearer x\"}");
        assertEquals(1, out.getJSONArray("queryParams").size());
        assertEquals("page", out.getJSONArray("queryParams").getJSONObject(0).getString("name"));
        assertEquals(1, out.getJSONArray("headerParams").size());
        assertEquals("Authorization", out.getJSONArray("headerParams").getJSONObject(0).getString("name"));
    }

    @Test
    void summarizeRequest_includesTypeAndConstraints() {
        String requestConfig = """
                {"configVersion":1,"method":"GET","queryParams":[{"name":"mobile","type":"string","required":true,"pattern":"^1\\\\d{10}$","maxLength":11,"value":"{{asset.demo.mobile}}"}],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none","json":{"schema":null,"example":null}}}
                """;
        var out = FlowDesignApiSummarizer.summarizeRequest(requestConfig, null);
        JSONObject mobile = out.getJSONArray("queryParams").getJSONObject(0);
        assertEquals("string", mobile.getString("type"));
        assertEquals("^1\\d{10}$", mobile.getString("pattern"));
        assertEquals(11, mobile.getIntValue("maxLength"));
        assertEquals("{{asset.demo.mobile}}", mobile.getString("valuePreview"));
        assertFalse(mobile.containsKey("enum"));
    }

    @Test
    void summarizeRequest_readsUrlencodedBodyParams() {
        String requestConfig = """
                {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"x-www-form-urlencoded","urlencoded":[{"name":"page","required":true}]}}
                """;
        var out = FlowDesignApiSummarizer.summarizeRequest(requestConfig, null);
        assertEquals(1, out.getJSONArray("bodyParams").size());
        assertEquals("page", out.getJSONArray("bodyParams").getJSONObject(0).getString("name"));
    }

    @Test
    void summarizeRequest_bodySchemaLeaves() {
        String requestConfig = """
                {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"schema":{"type":"object","properties":{"user":{"type":"object","properties":{"mobile":{"type":"string","pattern":"^1"}}}}},"example":null}}}
                """;
        var out = FlowDesignApiSummarizer.summarizeRequest(requestConfig, null);
        JSONArray leaves = out.getJSONArray("bodySchemaLeaves");
        assertEquals(1, leaves.size());
        assertEquals("user.mobile", leaves.getJSONObject(0).getString("path"));
        assertEquals("string", leaves.getJSONObject(0).getString("type"));
        assertEquals("^1", leaves.getJSONObject(0).getString("pattern"));
    }

    @Test
    void summarizeResponse_prefersResponsesSchemaLeaves() {
        var out = FlowDesignApiSummarizer.summarizeResponse(ApiConfigV2TestFixtures.RESPONSE_WITH_CODE_SCHEMA);
        assertFalse(out.isEmpty());
        assertEquals("integer", out.getString("code"));
    }

    @Test
    void summarizeResponse_ignoresNonV2Shape() {
        var out = FlowDesignApiSummarizer.summarizeResponse("{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\"}}}");
        assertTrue(out.isEmpty());
    }

    @Test
    void summarizeTestValueConfig_readsParamDefaults() {
        String tv = """
                {"request":{"paramDefaults":{"mobile":"{{asset.demo.mobile}}"},"bodyExample":"{\\"a\\":1}"},"response":{"examplesById":{"resp-1":{"ok":true}}}}
                """;
        var out = FlowDesignApiSummarizer.summarizeTestValueConfig(tv);
        assertEquals(1, out.getJSONArray("paramDefaults").size());
        assertEquals("mobile", out.getJSONArray("paramDefaults").getJSONObject(0).getString("name"));
        assertTrue(out.getString("bodyExample").contains("a"));
        assertEquals("resp-1", out.getJSONArray("responseExampleIds").getString(0));
    }

    @Test
    void suggestExtracts_fromDataPathFields() {
        var summary = new JSONObject();
        summary.put("code", "number");
        summary.put("msg", "string");
        summary.put("data.token", "string");
        summary.put("data.mobile", "string");
        var extracts = FlowDesignApiSummarizer.suggestExtracts(summary, "data");
        assertEquals(2, extracts.size());
        boolean hasToken = false;
        boolean hasMobile = false;
        for (int i = 0; i < extracts.size(); i++) {
            var row = extracts.getJSONObject(i);
            if ("token".equals(row.getString("name"))) {
                hasToken = true;
                assertEquals("$.data.token", row.getString("expr"));
            }
            if ("mobile".equals(row.getString("name"))) {
                hasMobile = true;
                assertEquals("$.data.mobile", row.getString("expr"));
            }
        }
        assertTrue(hasToken);
        assertTrue(hasMobile);
    }
}
