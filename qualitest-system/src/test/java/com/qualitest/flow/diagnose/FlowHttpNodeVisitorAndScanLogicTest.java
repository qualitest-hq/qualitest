package com.qualitest.flow.diagnose;

import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图遍历与按 API id 匹配引用的纯逻辑测试（不启 Spring）。
 */
class FlowHttpNodeVisitorAndScanLogicTest {

    @Test
    void visit_countsHttpNodesOnly() {
        String graph = """
                {"nodes":[
                  {"id":"h1","type":"http","data":{"callMode":"project","testProjectApiId":"10","name":"A"}},
                  {"id":"s1","type":"script","data":{"name":"脚本"}},
                  {"id":"h2","type":"http","data":{"callMode":"external","externalUrl":"https://x"}}
                ]}
                """;
        AtomicInteger httpCount = new AtomicInteger();
        String err = FlowHttpNodeVisitor.visit(graph, (id, node, data) -> httpCount.incrementAndGet());
        assertNull(err);
        assertEquals(2, httpCount.get());
    }

    @Test
    void isProjectBoundHttp_rules() {
        assertTrue(FlowHttpNodeVisitor.isProjectBoundHttp(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"callMode\":\"project\",\"testProjectApiId\":\"1\"}")));
        assertTrue(FlowHttpNodeVisitor.isProjectBoundHttp(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"testProjectApiId\":\"1\"}")));
        assertFalse(FlowHttpNodeVisitor.isProjectBoundHttp(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"callMode\":\"external\",\"externalUrl\":\"https://x\"}")));
        // callMode=project 即使暂未绑 id 也视为 project 节点（结构校验另报 warning）
        assertTrue(FlowHttpNodeVisitor.isProjectBoundHttp(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"callMode\":\"project\"}")));
        assertFalse(FlowHttpNodeVisitor.isProjectBoundHttp(
                com.alibaba.fastjson2.JSONObject.parseObject("{}")));
    }

    @Test
    void scanLogic_findsMatchingApiId() {
        String graph = """
                {"nodes":[
                  {"id":"n1","type":"http","data":{"callMode":"project","testProjectApiId":"100","name":"命中"}},
                  {"id":"n2","type":"http","data":{"callMode":"project","testProjectApiId":"200","name":"其他"}},
                  {"id":"n3","type":"http","data":{"callMode":"external","externalUrl":"https://x"}}
                ]}
                """;
        Long target = 100L;
        List<String> hitIds = new ArrayList<>();
        FlowHttpNodeVisitor.visit(graph, (nodeId, node, data) -> {
            if (!FlowHttpNodeVisitor.isProjectBoundHttp(data)) {
                return;
            }
            Long bound = FlowHttpNodeVisitor.parseTestProjectApiId(data.get("testProjectApiId"));
            if (target.equals(bound)) {
                hitIds.add(nodeId);
            }
        });
        assertEquals(List.of("n1"), hitIds);
    }

    @Test
    void visit_invalidJson_returnsError() {
        String err = FlowHttpNodeVisitor.visit("{not-json", (a, b, c) -> {
        });
        assertTrue(err != null && !err.isBlank());
    }
}
