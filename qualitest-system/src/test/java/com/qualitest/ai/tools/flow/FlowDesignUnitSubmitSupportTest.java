package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchMerger;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测谁：FlowDesignUnitSubmitSupport.submitEdges（批量边提交）。
 * 边界：normalizer、merger 为 mock，无 Spring；覆盖整批成功、部分失败不写 Capture、单边回执。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignUnitSubmitSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignUnitSubmitSupportTest {

    /** 规范化 mock：按调用次序返回成功或失败结果 */
    private FlowDesignPatchNormalizer normalizer;
    /** 图合并 mock：默认原样返回入参图 */
    private FlowDesignPatchMerger patchMerger;
    /** 被测提交流程 */
    private FlowDesignUnitSubmitSupport support;

    /** 初始化 mock 与被测对象；mergeAll 返回入参 base 图副本语义。 */
    @BeforeEach
    void setUp() {
        normalizer = mock(FlowDesignPatchNormalizer.class);
        patchMerger = mock(FlowDesignPatchMerger.class);
        support = new FlowDesignUnitSubmitSupport(normalizer, null, patchMerger);
        when(patchMerger.mergeAll(any(), any(), any())).thenAnswer(inv -> {
            GraphJson base = inv.getArgument(0);
            return base != null ? base : GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        });
    }

    /**
     * 前提：两元边 patch，normalize 均 ok。
     * 期望：received=true，Capture 含 2 条 addEdges。
     */
    @Test
    @Order(1)
    @DisplayName("多边全部通过写入 Capture")
    void submitEdges_allOk_recordsAll() {
        FlowDesignPatch patch = twoAddEdges("e1", "a", "b", "e2", "b", "c");
        AtomicInteger calls = new AtomicInteger();
        when(normalizer.normalizeUnit(any(), any(), any(), any())).thenAnswer(inv -> {
            FlowDesignPatch unit = inv.getArgument(0);
            calls.incrementAndGet();
            return new FlowDesignPatchNormalizer.NormalizeResult(unit, okValidation());
        });

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignToolContext ctx = baseCtx(capture);

        String json = support.submitEdges(patch, ctx, "submit_edges");
        JSONObject root = JSON.parseObject(json);

        assertTrue(root.getBooleanValue("received"));
        assertTrue(root.getJSONObject("validation").getBooleanValue("ok"));
        assertEquals(2, root.getJSONArray("unitIds").size());
        assertEquals(2, calls.get());
        assertTrue(capture.hasAccepted());
        assertEquals(2, capture.getNormalizedPatch().getAddEdges().size());
    }

    /**
     * 前提：两条边，第二条 normalize 失败。
     * 期望：received=false，Capture 仍为空（整批不写）。
     */
    @Test
    @Order(2)
    @DisplayName("部分失败整批不写 Capture")
    void submitEdges_partialFail_writesNothing() {
        FlowDesignPatch patch = twoAddEdges("e1", "a", "b", "e2", "b", "missing");
        AtomicInteger calls = new AtomicInteger();
        when(normalizer.normalizeUnit(any(), any(), any(), any())).thenAnswer(inv -> {
            FlowDesignPatch unit = inv.getArgument(0);
            int n = calls.incrementAndGet();
            if (n == 1) {
                return new FlowDesignPatchNormalizer.NormalizeResult(unit, okValidation());
            }
            return new FlowDesignPatchNormalizer.NormalizeResult(unit, failValidation("target 不存在"));
        });

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignToolContext ctx = baseCtx(capture);

        String json = support.submitEdges(patch, ctx, "submit_edges");
        JSONObject root = JSON.parseObject(json);

        assertFalse(root.getBooleanValue("received"));
        assertFalse(root.getJSONObject("validation").getBooleanValue("ok"));
        assertTrue(root.getString("error").contains("校验未通过"));
        assertFalse(capture.hasAccepted());
        assertNull(capture.getNormalizedPatch());
    }

    /**
     * 前提：单条边 patch。
     * 期望：回执含 unitId（单数）且 received=true。
     */
    @Test
    @Order(3)
    @DisplayName("单元素边回执含 unitId")
    void submitEdges_singleEdge_hasUnitId() {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddEdges().add(GraphEdge.builder().id("e1").source("a").target("b").build());
        when(normalizer.normalizeUnit(any(), any(), any(), any())).thenAnswer(inv ->
                new FlowDesignPatchNormalizer.NormalizeResult(inv.getArgument(0), okValidation()));

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        String json = support.submitEdges(patch, baseCtx(capture), "submit_edges");
        JSONObject root = JSON.parseObject(json);

        assertTrue(root.getBooleanValue("received"));
        assertEquals("addEdge:e1", root.getString("unitId"));
        assertEquals(1, root.getJSONArray("unitIds").size());
    }

    /**
     * 构造带 a/b/c 三节点空边图与指定 Capture 的工具上下文（全自动开启）。
     *
     * @param capture 本轮 submit 累积器
     * @return 上下文
     */
    private static FlowDesignToolContext baseCtx(FlowDesignSubmitCapture capture) {
        GraphJson graph = GraphJson.builder()
                .nodes(new ArrayList<>(List.of(
                        GraphNode.builder().id("a").type("http").data(new HashMap<>()).build(),
                        GraphNode.builder().id("b").type("http").data(new HashMap<>()).build(),
                        GraphNode.builder().id("c").type("http").data(new HashMap<>()).build()
                )))
                .edges(new ArrayList<>())
                .build();
        return FlowDesignToolContext.builder()
                .testProjectId(1L)
                .testFlowId(100L)
                .graphJson(graph)
                .submitCapture(capture)
                .autopilotEnabled(true)
                .build();
    }

    /**
     * 组装含两条新增边的 patch。
     *
     * @param id1 第一条边 id
     * @param s1  第一条边起点
     * @param t1  第一条边终点
     * @param id2 第二条边 id
     * @param s2  第二条边起点
     * @param t2  第二条边终点
     * @return 双 addEdges patch
     */
    private static FlowDesignPatch twoAddEdges(String id1, String s1, String t1,
                                              String id2, String s2, String t2) {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.getAddEdges().add(GraphEdge.builder().id(id1).source(s1).target(t1).build());
        patch.getAddEdges().add(GraphEdge.builder().id(id2).source(s2).target(t2).build());
        return patch;
    }

    /** @return 校验通过摘要 */
    private static DesignValidationResult okValidation() {
        return DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build();
    }

    /**
     * @param msg 错误文案
     * @return 校验失败摘要
     */
    private static DesignValidationResult failValidation(String msg) {
        return DesignValidationResult.builder().ok(false).errors(List.of(msg)).warnings(List.of()).build();
    }
}
