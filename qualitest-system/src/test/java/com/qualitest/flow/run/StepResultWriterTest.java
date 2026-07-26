package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link StepResultWriter} 单元测试：验证流程运行步骤结果的持久化格式。
 * <p>
 * 被测对象负责将运行时上下文（场景信息、env 快照、flow 变量）序列化为
 * {@link TestFlowRunStep} / {@link TestFlowRunStepResult} 实体，供落库与前端回放展示。
 * 其中 step_index=0 的「运行配置」步（{@code nodeType=run_config}）记录场景加载摘要。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=StepResultWriterTest
 */
class StepResultWriterTest {

    private final StepResultWriter writer = new StepResultWriter();

    /**
     * 从已解析的 {@link ResolvedRunScenario} 和 {@link FlowRunContext} 构建 step_index=0 的运行配置步。
     * 期望：nodeType=run_config、stepIndex=0；stepDetails 含 scenarioLoaded（场景 id/名称/环境 id/环境名/env 快照）
     * 和 flowAfter（当前 flow 变量快照）。
     */
    @Test
    void toRunConfigStepEntity_writesScenarioLoadedAndFlowAfter() {
        ResolvedRunScenario scenario = ResolvedRunScenario.builder()
                .scenarioId("sc-1")
                .scenarioName("冒烟")
                .testProjectEnvId(200L)
                .flowSeed(Map.of("loginUser", "admin"))
                .build();
        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "https://dev.example.com", "token", "t1"))
                .flow(Map.of("loginUser", "admin", "pollAttempt", 0))
                .build();

        TestFlowRunStep step = writer.toRunConfigStepEntity(9001L, scenario, "开发环境", ctx);

        assertEquals(0L, step.getStepIndex());
        assertEquals(StepResultWriter.NODE_TYPE_RUN_CONFIG, step.getNodeType());
        assertEquals(StepResultWriter.NODE_NAME_RUN_CONFIG, step.getNodeName());
        assertEquals("", step.getNodeId());

        var details = writer.parseStepDetails(step.getStepDetails());
        assertTrue(details.containsKey("scenarioLoaded"));
        assertTrue(details.containsKey("flowAfter"));

        var loaded = details.getJSONObject("scenarioLoaded");
        assertEquals("sc-1", loaded.getString("scenarioId"));
        assertEquals("冒烟", loaded.getString("scenarioName"));
        assertEquals("200", loaded.getString("testProjectEnvId"));
        assertEquals("开发环境", loaded.getString("envName"));
        assertNotNull(loaded.getJSONObject("envSnapshot"));
    }

    /**
     * 当运行时缺少 step_index=0 记录时，从 {@link TestFlowRunResult#getGraphJsonSnapshot()} 反推场景信息。
     * 期望：生成的 fallback 步含 scenarioLoaded 和 flowAfter（来自 graph meta.run.scenarios 的 flowSeed）。
     */
    @Test
    void buildFallbackRunConfigStepResult_derivesFromGraphSnapshot() {
        String snapshot = """
                {
                  "meta": {
                    "run": {
                      "activeScenarioId": "sc-1",
                      "scenarios": [
                        {
                          "id": "sc-1",
                          "name": "默认",
                          "testProjectEnvId": "201",
                          "flowSeed": { "x": 1 }
                        }
                      ]
                    }
                  },
                  "nodes": [],
                  "edges": []
                }
                """;
        TestFlowRunResult run = TestFlowRunResult.builder()
                .testFlowRunId(8001L)
                .runScenarioId("sc-1")
                .testProjectEnvId(201L)
                .graphJsonSnapshot(snapshot)
                .build();

        TestFlowRunStepResult step = writer.buildFallbackRunConfigStepResult(run, "测试环境");

        assertEquals(0L, step.getStepIndex());
        assertEquals(StepResultWriter.NODE_TYPE_RUN_CONFIG, step.getNodeType());
        var details = writer.parseStepDetails(step.getStepDetails());
        assertTrue(details.containsKey("scenarioLoaded"));
        assertEquals(1, details.getJSONObject("flowAfter").getIntValue("x"));
        assertTrue(writer.hasRunConfigStep(List.of(step)));
    }

    /**
     * {@link StepResultWriter#hasRunConfigStep} 应能识别已持久化的 step_index=0 + nodeType=run_config 记录。
     * 普通 http 步（stepIndex=1）不应被误判。
     */
    @Test
    void hasRunConfigStep_detectsPersistedStep0() {
        TestFlowRunStepResult step0 = TestFlowRunStepResult.builder()
                .stepIndex(0L)
                .nodeType(StepResultWriter.NODE_TYPE_RUN_CONFIG)
                .build();
        assertTrue(writer.hasRunConfigStep(List.of(step0)));
        assertFalse(writer.hasRunConfigStep(List.of(
                TestFlowRunStepResult.builder().stepIndex(1L).nodeType("http").build()
        )));
    }
}
