package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowDesignAssertNodeNormalizerTest {

    @Test
    void normalize_equalsToEq_andStripMustache() {
        Map<String, Object> data = new HashMap<>();
        data.put("rules", List.of(Map.of(
                "left", "{{flow.mobile}}",
                "operator", "equals",
                "right", "13800000001"
        )));

        FlowDesignAssertNodeNormalizer.normalize(data);

        @SuppressWarnings("unchecked")
        List<JSONObject> rules = (List<JSONObject>) (List<?>) data.get("rules");
        JSONObject rule = rules.get(0);
        assertEquals("flow.mobile", rule.getString("left"));
        assertEquals("eq", rule.getString("operator"));
        assertEquals("13800000001", rule.getString("right"));
    }

    @Test
    void normalizeOperator_aliases() {
        assertEquals("eq", FlowDesignAssertNodeNormalizer.normalizeOperator("EQUALS"));
        assertEquals("eq", FlowDesignAssertNodeNormalizer.normalizeOperator("=="));
        assertEquals("ne", FlowDesignAssertNodeNormalizer.normalizeOperator("!="));
        assertEquals("gt", FlowDesignAssertNodeNormalizer.normalizeOperator("gt"));
    }

    @Test
    void stripMustache_onlyFullWrap() {
        assertEquals("flow.x", FlowDesignAssertNodeNormalizer.stripMustache("{{flow.x}}"));
        assertEquals("{{flow.x}} suffix", FlowDesignAssertNodeNormalizer.stripMustache("{{flow.x}} suffix"));
        assertEquals(12, FlowDesignAssertNodeNormalizer.stripMustache(12));
    }
}
