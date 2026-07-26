package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行场景解析：从图内 meta.scenarios 选出本次 Run 使用的环境与 flow 初值。
 * <p>
 * 场景选择优先级：API 入参 scenarioId → activeScenarioId → 列表首条。
 * 环境 ID 优先级：API 入参 testProjectEnvId → 场景内配置。
 * <p>
 * 同时把场景上的 onNodeFailure、onSnapshotFailure 带入解析结果，供正式 Run 决定节点失败、checkpoint 失败时的处理方式。
 */
public final class RunScenarioBootstrap {

    private RunScenarioBootstrap() {
    }

    /**
     * 解析场景配置。
     *
     * @param graph              已迁移的图
     * @param scenarioIdOverride 入参场景 id，可覆盖 activeScenarioId
     * @param envIdOverride      入参环境 id，可覆盖场景内 testProjectEnvId
     * @return 场景 id、环境 id、flow 初值，以及节点/checkpoint 失败策略；策略未配置时为 null，由执行器填默认（fail / abort）
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride) {
        GraphMeta meta = graph != null ? graph.getMeta() : null;
        if (meta == null || meta.getScenarios() == null || meta.getScenarios().isEmpty()) {
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, "缺少 meta.scenarios");
        }

        String targetId = scenarioIdOverride;
        if (targetId == null || targetId.isBlank()) {
            targetId = meta.getActiveScenarioId();
        }
        if (targetId == null || targetId.isBlank()) {
            targetId = meta.getScenarios().get(0).getId();
        }

        GraphRunScenario matched = findScenario(meta.getScenarios(), targetId);
        if (matched == null) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    "运行场景不存在: " + targetId
            );
        }

        Long envId = envIdOverride;
        if (envId == null && matched.getTestProjectEnvId() != null && !matched.getTestProjectEnvId().isBlank()) {
            try {
                envId = Long.parseLong(matched.getTestProjectEnvId().trim());
            } catch (NumberFormatException e) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_GRAPH_INVALID,
                        "场景环境 ID 无效: " + matched.getTestProjectEnvId()
                );
            }
        }
        if (envId == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, "未指定 testProjectEnvId");
        }

        Map<String, Object> flowSeed = new HashMap<>();
        if (matched.getFlowSeed() != null) {
            flowSeed.putAll(matched.getFlowSeed());
        }

        return ResolvedRunScenario.builder()
                .scenarioId(matched.getId())
                .scenarioName(matched.getName())
                .testProjectEnvId(envId)
                .flowSeed(flowSeed)
                // 失败策略未配置时保持 null，执行器侧使用 fail / abort 默认
                .onNodeFailure(matched.getOnNodeFailure())
                .onSnapshotFailure(matched.getOnSnapshotFailure())
                .build();
    }

    private static GraphRunScenario findScenario(List<GraphRunScenario> scenarios, String id) {
        for (GraphRunScenario sc : scenarios) {
            if (sc != null && id != null && id.equals(sc.getId())) {
                return sc;
            }
        }
        return null;
    }
}
