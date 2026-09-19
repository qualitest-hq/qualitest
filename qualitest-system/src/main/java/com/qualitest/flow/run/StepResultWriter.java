package com.qualitest.flow.run;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.flow.snapshot.CheckpointAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 步骤结果落库：把内存中的 StepResult 转为 test_flow_run_step 表行。
 * <p>
 * 索引列（node_id、status、duration_ms 等）与 step_details JSON 分开存。
 * 除普通业务节点外，还负责 runConfig / snapshot / restore / resumeDecision 等审计步的构建。
 */
@Component
@RequiredArgsConstructor
public class StepResultWriter {

    /** 虚拟首步：记录 Run 启动时的场景、环境、flow 初值 */
    public static final String NODE_TYPE_RUN_CONFIG = "runConfig";

    public static final String NODE_NAME_RUN_CONFIG = "场景加载";

    /** 节点前 checkpoint 审计步类型 */
    public static final String NODE_TYPE_SNAPSHOT = "snapshot";

    public static final String NODE_NAME_SNAPSHOT = "数据快照";

    /** 续跑还原审计步类型 */
    public static final String NODE_TYPE_RESTORE = "restore";

    public static final String NODE_NAME_RESTORE = "数据还原";

    /** 用户 resume 决策审计步类型 */
    public static final String NODE_TYPE_RESUME_DECISION = "resumeDecision";

    public static final String NODE_NAME_RESUME_DECISION = "恢复决策";

    /**
     * 构建待插入的 {@link TestFlowRunStep}（不含 id / createTime）。
     */
    /**
     * 构建 step_index=0 的 runConfig 虚拟步（场景加载）。
     */
    public TestFlowRunStep toRunConfigStepEntity(Long testFlowRunId, ResolvedRunScenario scenario,
                                                 String envName, FlowRunContext ctx) {
        TestFlowRunStep step = new TestFlowRunStep();
        step.setTestFlowRunId(testFlowRunId);
        step.setStepIndex(0L);
        step.setNodeId("");
        step.setNodeType(NODE_TYPE_RUN_CONFIG);
        step.setNodeName(NODE_NAME_RUN_CONFIG);
        step.setStatus(RunStatus.PASSED.getCode());
        step.setDurationMs(0L);
        step.setStepDetails(toRunConfigStepDetailsJson(scenario, envName, ctx));
        step.setDelStatus(0);
        return step;
    }

    /**
     * runConfig 步的 step_details JSON：scenarioLoaded + 启动后 flow 初值 flowAfter。
     */
    public String toRunConfigStepDetailsJson(ResolvedRunScenario scenario, String envName, FlowRunContext ctx) {
        Map<String, Object> scenarioLoaded = new LinkedHashMap<>();
        if (scenario != null) {
            scenarioLoaded.put("scenarioId", scenario.getScenarioId());
            scenarioLoaded.put("scenarioName", scenario.getScenarioName());
            if (scenario.getTestProjectEnvId() != null) {
                scenarioLoaded.put("testProjectEnvId", String.valueOf(scenario.getTestProjectEnvId()));
            }
            scenarioLoaded.put("flowSeed", scenario.getFlowSeed() != null ? scenario.getFlowSeed() : Map.of());
        }
        scenarioLoaded.put("envName", envName);
        if (ctx != null && ctx.getEnv() != null) {
            scenarioLoaded.put("envSnapshot", ctx.getEnv());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scenarioLoaded", scenarioLoaded);
        if (ctx != null && ctx.getFlow() != null && !ctx.getFlow().isEmpty()) {
            details.put("flowAfter", ctx.getFlow());
        }
        return JSON.toJSONString(details);
    }

    /**
     * 步骤列表是否已含落库的 runConfig 首步。
     */
    public boolean hasRunConfigStep(List<TestFlowRunStepResult> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        return steps.stream().anyMatch(s ->
                s.getStepIndex() != null
                        && s.getStepIndex() == 0L
                        && NODE_TYPE_RUN_CONFIG.equals(s.getNodeType())
        );
    }

    /**
     * 历史 Run 无 step0 时，从 Run 头与 graph 快照推导降级 runConfig 步（env 初值未冻结）。
     */
    public TestFlowRunStepResult buildFallbackRunConfigStepResult(TestFlowRunResult run, String envName) {
        final String runScenarioId = run.getRunScenarioId();
        String scenarioName = null;
        Map<String, Object> flowSeed = new HashMap<>();
        String scenarioId = runScenarioId;

        if (run.getGraphJsonSnapshot() != null && !run.getGraphJsonSnapshot().isBlank()) {
            try {
                GraphJson graph = GraphJson.parse(run.getGraphJsonSnapshot());
                if (graph.getMeta() != null && graph.getMeta().getScenarios() != null) {
                    List<GraphRunScenario> scenarios = graph.getMeta().getScenarios();
                    if (scenarios != null && !scenarios.isEmpty()) {
                        GraphRunScenario matched = scenarios.stream()
                                .filter(s -> Objects.equals(s.getId(), runScenarioId))
                                .findFirst()
                                .orElse(scenarios.get(0));
                        if (scenarioId == null) {
                            scenarioId = matched.getId();
                        }
                        scenarioName = matched.getName();
                        if (matched.getFlowSeed() != null) {
                            flowSeed.putAll(matched.getFlowSeed());
                        }
                    }
                }
            } catch (Exception ignored) {
                // 快照解析失败时仍返回最小 step0
            }
        }

        Map<String, Object> scenarioLoaded = new LinkedHashMap<>();
        scenarioLoaded.put("scenarioId", scenarioId);
        scenarioLoaded.put("scenarioName", scenarioName);
        if (run.getTestProjectEnvId() != null) {
            scenarioLoaded.put("testProjectEnvId", String.valueOf(run.getTestProjectEnvId()));
        }
        scenarioLoaded.put("envName", envName);
        scenarioLoaded.put("flowSeed", flowSeed);
        Map<String, Object> envSnapshot = new LinkedHashMap<>();
        envSnapshot.put("testProjectEnvId", scenarioLoaded.get("testProjectEnvId"));
        envSnapshot.put("envName", envName);
        scenarioLoaded.put("envSnapshot", envSnapshot);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scenarioLoaded", scenarioLoaded);
        if (!flowSeed.isEmpty()) {
            details.put("flowAfter", flowSeed);
        }

        return TestFlowRunStepResult.builder()
                .testFlowRunId(run.getTestFlowRunId())
                .stepIndex(0L)
                .nodeId("")
                .nodeType(NODE_TYPE_RUN_CONFIG)
                .nodeName(NODE_NAME_RUN_CONFIG)
                .status(RunStatus.PASSED.getCode())
                .durationMs(0L)
                .stepDetails(JSON.toJSONString(details))
                .build();
    }

    /**
     * checkpoint 审计步：nodeId 为开启 snapshotBefore 的业务节点。
     * step_details.snapshot 含 snapshotId、scope、tables、label、resetEndpoint；失败时另有 error。
     */
    public StepResult toSnapshotStepResult(CheckpointAttempt attempt) {
        Map<String, Object> snapshotDetails = new LinkedHashMap<>();
        snapshotDetails.put("label", attempt.getLabel());
        snapshotDetails.put("resetEndpoint", attempt.getResetEndpointBase());
        if (attempt.getScope() != null) {
            snapshotDetails.put("scope", attempt.getScope().getScope());
            snapshotDetails.put("tables", attempt.getScope().getTables());
        }
        if (attempt.getSnapshotRef() != null) {
            snapshotDetails.put("snapshotId", attempt.getSnapshotRef().getSnapshotId());
            snapshotDetails.put("createdAt", attempt.getSnapshotRef().getCreatedAt());
            snapshotDetails.put("status", attempt.getSnapshotRef().getStatus());
        }

        StepResult.StepResultBuilder builder = StepResult.builder()
                .nodeId(attempt.getNode().getId())
                .nodeType(NODE_TYPE_SNAPSHOT)
                .nodeName(NODE_NAME_SNAPSHOT)
                .durationMs(attempt.getDurationMs())
                .snapshot(snapshotDetails);

        if (attempt.isPassed()) {
            builder.status(RunStatus.PASSED.getCode());
        } else {
            FlowErrorCode code = attempt.getErrorCode() != null
                    ? attempt.getErrorCode()
                    : FlowErrorCode.TF_SNAPSHOT_FAILED;
            builder.status(RunStatus.FAILED.getCode())
                    .error(StepError.of(code, attempt.getErrorMessage()));
        }
        return builder.build();
    }

    /**
     * resume 决策审计步：记录 decision、snapshotId、operator。
     * continueWithInput 时额外写入 inputs 摘要（key 名含 password 的值脱敏为 ***）。
     */
    public StepResult toResumeDecisionStepResult(ResumeDecision decision) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("decision", decision.getDecision());
        if (decision.getSnapshotId() != null) {
            details.put("snapshotId", decision.getSnapshotId());
        }
        if (decision.getOperator() != null) {
            details.put("operator", decision.getOperator());
        }
        if (decision.isContinueWithInput() && decision.getInputs() != null) {
            details.put("inputs", sanitizeInputsSummary(decision.getInputs()));
        }
        return StepResult.builder()
                .nodeId("")
                .nodeType(NODE_TYPE_RESUME_DECISION)
                .nodeName(NODE_NAME_RESUME_DECISION)
                .status(RunStatus.PASSED.getCode())
                .durationMs(0L)
                .snapshot(details)
                .build();
    }

    /**
     * 审计摘要脱敏：字段名（忽略大小写）包含 password 时值改为 ***。
     */
    private static Map<String, Object> sanitizeInputsSummary(Map<String, Object> inputs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : inputs.entrySet()) {
            String key = e.getKey() != null ? e.getKey() : "";
            if (key.toLowerCase().contains("password")) {
                out.put(key, "***");
            } else {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    /**
     * restore 审计步：记录 snapshotId、resetEndpoint、还原耗时。
     * StepResult.snapshot 字段此处表示 test-support 操作详情，非流程图快照。
     */
    public StepResult toRestoreStepResult(String nodeId, String snapshotId, String resetBase, long durationMs) {
        Map<String, Object> restoreDetails = new LinkedHashMap<>();
        restoreDetails.put("snapshotId", snapshotId);
        restoreDetails.put("resetEndpoint", resetBase);
        restoreDetails.put("status", "restored");

        return StepResult.builder()
                .nodeId(nodeId != null ? nodeId : "")
                .nodeType(NODE_TYPE_RESTORE)
                .nodeName(NODE_NAME_RESTORE)
                .status(RunStatus.PASSED.getCode())
                .durationMs(durationMs)
                .snapshot(restoreDetails)
                .build();
    }

    public TestFlowRunStep toEntity(Long testFlowRunId, int stepIndex, StepResult result) {
        TestFlowRunStep step = new TestFlowRunStep();
        step.setTestFlowRunId(testFlowRunId);
        step.setStepIndex((long) stepIndex);
        step.setNodeId(result.getNodeId());
        step.setNodeType(result.getNodeType());
        step.setNodeName(result.getNodeName());
        step.setStatus(result.getStatus());
        step.setDurationMs(result.getDurationMs());
        step.setEdgeId(result.getEdgeId());
        step.setStepDetails(toStepDetailsJson(result));
        step.setDelStatus(0);
        return step;
    }

    /**
     * 将步骤专属详情序列化为 JSON 字符串，写入 {@code step_details} 列。
     */
    public String toStepDetailsJson(StepResult result) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (result.getHttp() != null) {
            details.put("http", result.getHttp());
        }
        if (result.getAssertDetails() != null) {
            details.put("assert", result.getAssertDetails());
        }
        if (result.getExtracts() != null) {
            details.put("extracts", result.getExtracts());
        }
        if (result.getBranchTaken() != null && !result.getBranchTaken().isEmpty()) {
            details.put("branchTaken", result.getBranchTaken());
        }
        if (result.getAssigns() != null) {
            details.put("assigns", result.getAssigns());
        }
        if (result.getScript() != null && !result.getScript().isEmpty()) {
            details.put("script", result.getScript());
        }
        if (result.getSubflow() != null && !result.getSubflow().isEmpty()) {
            details.put("subflow", result.getSubflow());
        }
        if (result.getSnapshot() != null && !result.getSnapshot().isEmpty()) {
            details.put("snapshot", result.getSnapshot());
        }
        if (result.getFlowAfter() != null && !result.getFlowAfter().isEmpty()) {
            details.put("flowAfter", result.getFlowAfter());
        }
        StepError error = result.getError();
        if (error != null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", error.getCode());
            err.put("message", error.getMessage());
            details.put("error", err);
        }
        return JSON.toJSONString(details);
    }

    /**
     * 对 graph_json snapshot 计算 SHA-256 指纹。
     */
    public static String fingerprint(String snapshotJson) {
        if (snapshotJson == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(snapshotJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject parseStepDetails(String json) {
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        return JSON.parseObject(json);
    }
}
