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
 * 测 StepResultWriter：run_config 步序列化、graph 快照回退、hasRunConfigStep 识别。
 * 边界：无落库记录时从 graphJsonSnapshot 反推。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=StepResultWriterTest
 */
class StepResultWriterTest {

    private final StepResultWriter writer = new StepResultWriter();

    /**
     * 前提：ResolvedRunScenario + ctx 含 env/flow。
     * 期望：stepIndex=0、nodeType=run_config；details 含 scenarioLoaded 与 flowAfter。
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
     * 前提：run 无 step0，仅有 graphJsonSnapshot（sc-1 + flowSeed.x=1）。
     * 期望：fallback 步含 scenarioLoaded；flowAfter.x=1；hasRunConfigStep=true。
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
     * 前提：列表分别为 run_config@0 与普通 http@1。
     * 期望：前者 true，后者 false。
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
