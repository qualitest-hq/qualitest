package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.project.domain.TestPluginEnvBinding;

import java.util.List;

/**
 * 开跑策略字段解析与合成：入口 {@code data.maxSteps}、存档点插件环境 {@code onSnapshotFailure}。
 */
public final class EntryRunPolicySupport {

    /** 被测库存档点插件 id */
    public static final String ARCHIVE_POINT_PLUGIN_ID = "qualitest.sut-db-archive-point";

    private EntryRunPolicySupport() {
    }

    /**
     * 解析本次入口节点 id：优先上下文已写的 entryNodeId，否则用场景解析结果。
     */
    public static String resolveEntryNodeId(FlowRunContext ctx, ResolvedRunScenario scenario) {
        if (ctx != null && ctx.getEntryNodeId() != null && !ctx.getEntryNodeId().isBlank()) {
            return ctx.getEntryNodeId().trim();
        }
        if (scenario != null && scenario.getEntryNodeId() != null && !scenario.getEntryNodeId().isBlank()) {
            return scenario.getEntryNodeId().trim();
        }
        return null;
    }

    /**
     * 从入口与环境绑定合成开跑策略。
     *
     * @param graph       运行图快照
     * @param entryNodeId 入口节点 id，可空（maxSteps 走默认）
     * @param bindings    环境已启用插件绑定，可空
     */
    public static RunSnapshotPolicy compose(
            GraphJson graph,
            String entryNodeId,
            List<TestPluginEnvBinding> bindings
    ) {
        return RunSnapshotPolicy.compose(
                maxStepsOfEntry(graph, entryNodeId),
                onSnapshotFailureFromBindings(bindings));
    }

    /**
     * 读取入口节点 {@code data.maxSteps}；缺节点 / 缺字段 / 非法 → null（由策略侧补默认）。
     */
    public static Integer maxStepsOfEntry(GraphJson graph, String entryNodeId) {
        if (graph == null || entryNodeId == null || entryNodeId.isBlank()) {
            return null;
        }
        GraphNode node = GraphLookupUtils.findNode(graph.getNodes(), entryNodeId);
        if (node == null || node.getData() == null) {
            return null;
        }
        return coercePositiveInt(node.getData().get("maxSteps"));
    }

    /**
     * 从已启用环境绑定中取存档点插件的 {@code onSnapshotFailure}；未启用或未配 → null。
     */
    public static String onSnapshotFailureFromBindings(List<TestPluginEnvBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        for (TestPluginEnvBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            if (binding.getDelStatus() != null && binding.getDelStatus() != 0) {
                continue;
            }
            if (binding.getEnabled() == null || binding.getEnabled() != 1) {
                continue;
            }
            if (!ARCHIVE_POINT_PLUGIN_ID.equals(binding.getPluginId())) {
                continue;
            }
            JSONObject config = parseConfig(binding.getConfigJson());
            Object raw = config.get("onSnapshotFailure");
            if (raw == null) {
                return null;
            }
            String s = String.valueOf(raw).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static Integer coercePositiveInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject o = JSON.parseObject(configJson);
            return o == null ? new JSONObject() : o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
