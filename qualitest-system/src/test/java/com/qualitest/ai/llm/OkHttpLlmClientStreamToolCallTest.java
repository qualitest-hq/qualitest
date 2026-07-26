package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link OkHttpLlmClient.StreamToolCallAccumulator} 单元测试：验证 OpenAI 流式 {@code delta.tool_calls} 分片聚合。
 * <p>
 * SSE 模式下 tool_calls 的 id、name、arguments 会分多帧到达；被测对象按 {@code index} 合并后，
 * {@link OkHttpLlmClient#chatStream} 才能将完整 {@link LlmToolCall} 交给 {@link AiAgentRunner} 执行工具。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=OkHttpLlmClientStreamToolCallTest
 */
class OkHttpLlmClientStreamToolCallTest {

    /**
     * 单工具多帧 arguments 增量拼接。
     * 期望：聚合后 id/name/argumentsJson 与完整 tool_call 一致。
     */
    @Test
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
     * 同一轮响应并行返回多个 tool_calls（不同 index）。
     * 期望：按 index 顺序产出两条完整 LlmToolCall，互不覆盖。
     */
    @Test
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
