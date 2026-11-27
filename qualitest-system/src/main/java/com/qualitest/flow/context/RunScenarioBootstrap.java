package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行场景解析：选出本次 Run 的环境与入参值树。
 * <p>
 * 仅从 {@code scenario-entry.data.scenarios} 读取（可指定 entryNodeId）。
 * 场景选择：入参 scenarioId；未指定时仅当入口恰好一个场景才采用该场景，多个则报错。
 * 环境：入参 env 覆盖 → 场景 envId → fallbackEnvId。
 */
public final class RunScenarioBootstrap {

    private RunScenarioBootstrap() {
    }

    /** 解析场景：可覆盖场景 id 与环境 id，其余走默认。 */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride) {
        return resolve(graph, scenarioIdOverride, envIdOverride, null, null, null);
    }

    /** 解析场景：额外提供环境缺失时的回落 envId。 */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId) {
        return resolve(graph, scenarioIdOverride, envIdOverride, fallbackEnvId, null, null);
    }

    /**
     * 解析场景配置（不指定入口节点 id；可带环境缺失提示文案）。
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId, String availableEnvHint) {
        return resolve(graph, scenarioIdOverride, envIdOverride, fallbackEnvId, availableEnvHint, null);
    }

    /**
     * 解析场景配置。
     *
     * @param entryNodeIdOverride 指定 scenario-entry 节点 id；可空则仅当图上恰好一个场景入口时使用
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId, String availableEnvHint,
                                              String entryNodeIdOverride) {
        GraphNode entry = EntryScenarioSupport.findScenarioEntry(graph, entryNodeIdOverride);
        if (entry == null) {
            List<GraphNode> entries = EntryScenarioSupport.listScenarioEntries(graph);
            if (entries.size() > 1) {
                throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID,
                        "存在多个场景入口，须指定 entryNodeId");
            }
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID,
                    "缺少运行场景：请在 scenario-entry 配置 scenarios");
        }
        List<GraphRunScenario> entryScenarios = EntryScenarioSupport.scenariosOf(entry);
        if (entryScenarios.isEmpty()) {
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID,
                    "缺少运行场景：请在 scenario-entry 配置 scenarios");
        }
        return resolveFromList(entryScenarios, scenarioIdOverride, envIdOverride, fallbackEnvId,
                availableEnvHint, entry.getId());
    }

    private static ResolvedRunScenario resolveFromList(List<GraphRunScenario> scenarios,
                                                       String scenarioIdOverride,
                                                       Long envIdOverride,
                                                       Long fallbackEnvId,
                                                       String availableEnvHint,
                                                       String entryNodeId) {
        String targetId = scenarioIdOverride;
        if (targetId == null || targetId.isBlank()) {
            if (scenarios.size() != 1) {
                throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID,
                        "未指定运行场景，且入口有多个场景，请显式选择");
            }
            targetId = scenarios.get(0).getId();
        }

        GraphRunScenario matched = findScenario(scenarios, targetId);
        if (matched == null) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    "运行场景不存在: " + targetId
            );
        }

        Long envId = envIdOverride;
        String envStr = matched.getEnvId() != null && !matched.getEnvId().isBlank()
                ? matched.getEnvId().trim() : null;
        if (envId == null && envStr != null) {
            try {
                envId = Long.parseLong(envStr);
            } catch (NumberFormatException e) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_GRAPH_INVALID,
                        "场景环境 ID 无效: " + envStr
                );
            }
        }
        if (envId == null) {
            envId = fallbackEnvId;
        }
        if (envId == null) {
            StringBuilder msg = new StringBuilder(
                    "未指定环境；请传入 envId 或在场景上绑定 envId，不会自动选环境");
            if (availableEnvHint != null && !availableEnvHint.isBlank()) {
                msg.append("。可选：").append(availableEnvHint.trim());
            } else {
                msg.append("。请先 list_project_envs 查看可用环境");
            }
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, msg.toString());
        }

        Map<String, Object> inSeed = matched.getInSeed() != null
                ? new HashMap<>(matched.getInSeed()) : new HashMap<>();

        return ResolvedRunScenario.builder()
                .scenarioId(matched.getId())
                .scenarioName(matched.getName())
                .entryNodeId(entryNodeId)
                .testProjectEnvId(envId)
                .inSeed(inSeed)
                .build();
    }

    /**
     * 把项目环境列表格式化为报错用的可复制片段。
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