package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 测 OkHttpLlmClient.StreamToolCallAccumulator：OpenAI 流式 delta.tool_calls 分片聚合。
 * 边界：纯累加器，不发 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=OkHttpLlmClientStreamToolCallTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OkHttpLlmClientStreamToolCallTest {

    /**
     * 前提：单工具多帧增量拼接 id/name/arguments。
     * 期望：聚合后 id/name/argumentsJson 完整一致。
     */
    @Test
    @Order(1)
    @DisplayName("增量 delta 聚合为完整 tool_call")
    void accumulator_mergesIncrementalToolCallDeltas() {
        OkHttpLlmClient.StreamToolCallAccumulator accumulator = new OkHttpLlmClient.StreamToolCallAccumulator();

        accumulator.appendDelta(JSON.parseArray("""
                [{"index":0,"id":"call_abc","type":"function","function":{"name":"search_apis","arguments":""}}]
                """));
        accumulator.appendDelta(JSON.parseArray("""
                [{"index":0,"function":{"arguments":"{\\"keyword\\":\\"\\""}}]
                """));
        accumulator.appendDelta(JSON.parseArray("""
                [{"index":0,"function":{"arguments":"}"}}]
                """));

        List<LlmToolCall> toolCalls = accumulator.build();
        assertEquals(1, toolCalls.size());
        LlmToolCall call = toolCalls.get(0);
        assertEquals("call_abc", call.getId());
        assertEquals("search_apis", call.getName());
        assertEquals("{\"keyword\":\"\"}", call.getArgumentsJson());
    }

    /**
     * 前提：同一轮并行两个 index 的 tool_calls。
     * 期望：按 index 产出两条完整 LlmToolCall，互不覆盖。
     */
    @Test
    @Order(2)
    @DisplayName("并行多 index 产出多条 tool_call")
    void accumulator_supportsMultipleParallelToolCalls() {
        OkHttpLlmClient.StreamToolCallAccumulator accumulator = new OkHttpLlmClient.StreamToolCallAccumulator();

        JSONArray first = JSON.parseArray("""
                [
                  {"index":0,"id":"call_1","function":{"name":"get_graph_summary","arguments":""}},
                  {"index":1,"id":"call_2","function":{"name":"search_apis","arguments":""}}
                ]
                """);
        accumulator.appendDelta(first);
        accumulator.appendDelta(JSON.parseArray("""
                [{"index":1,"function":{"arguments":"{}"}}]
                """));

        List<LlmToolCall> toolCalls = accumulator.build();
        assertEquals(2, toolCalls.size());
        assertEquals("get_graph_summary", toolCalls.get(0).getName());
        assertEquals("search_apis", toolCalls.get(1).getName());
        assertNotNull(toolCalls.get(1).getArgumentsJson());
    }
}
