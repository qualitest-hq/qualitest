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
 * 场景选择优先级：入参 scenarioId → activeScenarioId → 列表首条。
 * 环境 ID 优先级：入参 testProjectEnvId → 场景内配置 → 可选 fallbackEnvId。
 * 三者皆空时硬失败，不会自动挑选项目「默认环境」；可传入 availableEnvHint，
 * 把可选 {@code envId=…（名称）} 拼进报错，便于复制后显式绑定。
 * <p>
 * 同时把场景上的 onNodeFailure、onSnapshotFailure 带入解析结果，供正式 Run 决定节点失败、checkpoint 失败时的处理方式。
 */
public final class RunScenarioBootstrap {

    private RunScenarioBootstrap() {
    }

    /**
     * 解析场景配置（不使用额外回落环境；缺环境时直接失败）。
     *
     * @param graph              已迁移的图
     * @param scenarioIdOverride 入参场景 id，可覆盖 activeScenarioId
     * @param envIdOverride      入参环境 id，可覆盖场景内 testProjectEnvId
     * @return 场景 id、环境 id、flow 初值，以及节点/checkpoint 失败策略
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride) {
        return resolve(graph, scenarioIdOverride, envIdOverride, null);
    }

    /**
     * 解析场景配置。
     *
     * @param graph              已迁移的图
     * @param scenarioIdOverride 入参场景 id，可覆盖 activeScenarioId
     * @param envIdOverride      入参环境 id，可覆盖场景内 testProjectEnvId
     * @param fallbackEnvId      入参与场景均无 env 时的可选回落；正式触发跑流应传 null
     * @return 场景 id、环境 id、flow 初值，以及节点/checkpoint 失败策略；策略未配置时为 null，由执行器填默认（fail / abort）
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId) {
        return resolve(graph, scenarioIdOverride, envIdOverride, fallbackEnvId, null);
    }

    /**
     * 解析场景配置。
     * 环境 id 优先级：入参 envIdOverride → 场景已绑 testProjectEnvId → fallbackEnvId。
     * 三者皆空时硬失败（不自动挑「默认环境」）；若传入 availableEnvHint 则拼进报错，便于复制 envId。
     *
     * @param availableEnvHint 缺 env 时报错附带的可选环境列表（如 envId=1（默认环境）），可空
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId, String availableEnvHint) {
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
            envId = fallbackEnvId;
        }
        if (envId == null) {
            // 正式跑流不自动选环境：须场景已绑或入参覆盖；报错里附可选 envId 便于显式选择
            StringBuilder msg = new StringBuilder(
                    "未指定 testProjectEnvId；请传入或 submit_scenario 绑定，不会自动选环境");
            if (availableEnvHint != null && !availableEnvHint.isBlank()) {
                msg.append("。可选：").append(availableEnvHint.trim());
            } else {
                msg.append("。请先 list_project_envs 查看可用环境");
            }
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, msg.toString());
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

    /**
     * 把项目环境列表格式化为报错用的可复制片段。
     * 形如 {@code envId=1（默认环境）；envId=2（预发）}，最多列 8 个。
     */
    public static String formatAvailableEnvHint(List<EnvHint> envs) {
        if (envs == null || envs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (EnvHint e : envs) {
            if (e == null || e.id() == null) {
                continue;
            }
            if (n > 0) {
                sb.append("；");
            }
            sb.append("envId=").append(e.id());
            if (e.name() != null && !e.name().isBlank()) {
                sb.append("（").append(e.name().trim()).append("）");
            }
            n++;
            if (n >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 报错提示用的环境摘要：环境主键 + 展示名。
     */
    public record EnvHint(Long id, String name) {}

    private static GraphRunScenario findScenario(List<GraphRunScenario> scenarios, String id) {
        for (GraphRunScenario sc : scenarios) {
            if (sc != null && id != null && id.equals(sc.getId())) {
                return sc;
            }
        }
        return null;
    }
}
