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
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowHttpNodeVisitorAndScanLogicTest
 */
class FlowHttpNodeVisitorAndScanLogicTest {

    /**
     * 前提：图含 2 个 http 节点与 1 个 script 节点。
     * 期望：visit 回调仅对 http 节点计数，共 2 次，无错误。
     */
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

    /**
     * 前提：多种 callMode 与 testProjectApiId 组合的节点 data。
     * 期望：project/绑 id 为 true，external/空 data 为 false。
     */
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

    /**
     * 前提：图中 project 节点分别绑定 API 100 与 200。
     * 期望：扫描 target=100 时仅命中 n1。
     */
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

    /**
     * 前提：传入非法 JSON 字符串。
     * 期望：visit 返回非空错误信息。
     */
    @Test
    void visit_invalidJson_returnsError() {
        String err = FlowHttpNodeVisitor.visit("{not-json", (a, b, c) -> {
        });
        assertTrue(err != null && !err.isBlank());
    }
}
