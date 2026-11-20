package com.qualitest.flow.context;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphInputPreset;
import com.qualitest.flow.model.GraphInputSpec;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开跑前解析：从本图选出本次要用的运行场景、环境 id，以及 in 初值。
 * <p>
 * 场景选择顺序：触发参数里的 scenarioId → 图上活动场景 → 场景列表第一条。<br>
 * {@code in} 初值：场景 {@code inSeed}（字段存在即用，可为空表）→ 否则回落旧 {@code meta.inputPresets}。<br>
 * 环境选择顺序：触发参数环境 → 场景绑定环境 → 调用方传入的 fallback。
 * 全部为空则抛错，不会自动挑项目「默认环境」。
 */
public final class RunScenarioBootstrap {

    private RunScenarioBootstrap() {
    }

    /** 解析场景与入参；不使用 fallback 环境，不覆盖预设。 */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride) {
        return resolve(graph, scenarioIdOverride, envIdOverride, null, null, null);
    }

    /** 解析场景与入参；可带 fallback 环境。 */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId) {
        return resolve(graph, scenarioIdOverride, envIdOverride, fallbackEnvId, null, null);
    }

    /**
     * 解析场景与入参。
     *
     * @param availableEnvHint 缺环境时报错附带的可选环境说明，可空
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId, String availableEnvHint) {
        return resolve(graph, scenarioIdOverride, envIdOverride, fallbackEnvId, availableEnvHint, null);
    }

    /**
     * 解析场景与入参（完整参数）。
     *
     * @param inputPresetIdOverride 显式指定旧入参预设 id（仅当场景尚无 inSeed 字段时生效）
     */
    public static ResolvedRunScenario resolve(GraphJson graph, String scenarioIdOverride, Long envIdOverride,
                                              Long fallbackEnvId, String availableEnvHint,
                                              String inputPresetIdOverride) {
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
            envId = parseEnvId(matched.getTestProjectEnvId(), "场景");
        }
        if (envId == null) {
            envId = fallbackEnvId;
        }
        if (envId == null) {
            StringBuilder msg = new StringBuilder(
                    "未指定 testProjectEnvId；请传入或 submit_scenario 绑定，不会自动选环境");
            if (availableEnvHint != null && !availableEnvHint.isBlank()) {
                msg.append("。可选：").append(availableEnvHint.trim());
            } else {
                msg.append("。请先 list_project_envs 查看可用环境");
            }
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, msg.toString());
        }

        Map<String, Object> inSeed = new HashMap<>();
        String presetId = null;
        String presetName = null;
        String onNodeFailure = matched.getOnNodeFailure();
        String onSnapshotFailure = matched.getOnSnapshotFailure();
        Integer maxSteps = matched.getMaxSteps();

        if (matched.getInSeed() != null) {
            inSeed.putAll(matched.getInSeed());
        } else {
            GraphInputPreset preset = resolveInputPreset(meta, inputPresetIdOverride);
            if (preset != null) {
                presetId = preset.getId();
                presetName = preset.getName();
                if (preset.getValues() != null) {
                    inSeed.putAll(preset.getValues());
                }
                if (preset.getOnNodeFailure() != null && !preset.getOnNodeFailure().isBlank()) {
                    onNodeFailure = preset.getOnNodeFailure();
                }
                if (preset.getOnSnapshotFailure() != null && !preset.getOnSnapshotFailure().isBlank()) {
                    onSnapshotFailure = preset.getOnSnapshotFailure();
                }
                if (preset.getMaxSteps() != null) {
                    maxSteps = preset.getMaxSteps();
                }
            }
        }

        return ResolvedRunScenario.builder()
                .scenarioId(matched.getId())
                .scenarioName(matched.getName())
                .testProjectEnvId(envId)
                .inSeed(inSeed)
                .inputPresetId(presetId)
                .inputPresetName(presetName)
                .onNodeFailure(onNodeFailure)
                .onSnapshotFailure(onSnapshotFailure)
                .maxSteps(maxSteps)
                .build();
    }

    /**
     * 从 meta 选出一条旧入参预设（仅兼容未写入场景 inSeed 的图）。
     * 无预设列表时返回 null。
     *
     * @param presetIdOverride 显式指定的预设 id；空则按活动预设 / 入参定义默认 / 列表首条
     */
    public static GraphInputPreset resolveInputPreset(GraphMeta meta, String presetIdOverride) {
        if (meta == null || meta.getInputPresets() == null || meta.getInputPresets().isEmpty()) {
            return null;
        }
        String targetId = presetIdOverride;
        if (targetId == null || targetId.isBlank()) {
            targetId = meta.getActiveInputPresetId();
        }
        if (targetId == null || targetId.isBlank()) {
            GraphInputSpec spec = meta.getInputSpec();
            if (spec != null && spec.getDefaultPresetId() != null && !spec.getDefaultPresetId().isBlank()) {
                targetId = spec.getDefaultPresetId();
            }
        }
        if (targetId == null || targetId.isBlank()) {
            return meta.getInputPresets().get(0);
        }
        for (GraphInputPreset p : meta.getInputPresets()) {
            if (p != null && targetId.equals(p.getId())) {
                return p;
            }
        }
        throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, "入参预设不存在: " + targetId);
    }

    /**
     * 把项目环境列表拼成报错用短文，形如 {@code envId=1（默认环境）；envId=2（预发）}，最多 8 条。
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

    /** 报错提示用的环境摘要：主键 + 展示名 */
    public record EnvHint(Long id, String name) {}

    private static Long parseEnvId(String raw, String source) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, source + "环境 ID 无效: " + raw);
        }
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
