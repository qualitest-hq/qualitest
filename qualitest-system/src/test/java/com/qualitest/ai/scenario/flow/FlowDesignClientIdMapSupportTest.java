package com.qualitest.ai.scenario.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowDesignClientIdMapSupport：biz_ref 读写与按雪花摘除。
 */
class FlowDesignClientIdMapSupportTest {

    @Test
    @DisplayName("写回 biz_ref 保留 testFlowId，并可读回映射")
    void writeAndParse_roundTrip() {
        String biz = "{\"testFlowId\":\"100\"}";
        Map<String, String> map = new HashMap<>();
        map.put("n_login", "9001");
        String next = FlowDesignClientIdMapSupport.writeToBizRef(biz, map);
        assertTrue(next.contains("testFlowId"));
        Map<String, String> parsed = FlowDesignClientIdMapSupport.parseFromBizRef(next);
        assertEquals("9001", parsed.get("n_login"));
    }

    @Test
    @DisplayName("按雪花 id 摘除短名")
    void pruneBySnowflakeIds_removesMatching() {
        Map<String, String> map = new HashMap<>();
        map.put("n_a", "1");
        map.put("n_b", "2");
        assertTrue(FlowDesignClientIdMapSupport.pruneBySnowflakeIds(map, List.of("1")));
        assertFalse(map.containsKey("n_a"));
        assertEquals("2", map.get("n_b"));
    }
}
