package com.qualitest.ai.scenario.flow;

import com.qualitest.flow.validate.GraphNodeBooleanFieldRules;

import java.util.Locale;
import java.util.Map;

/**
 * 按节点 type 分发 data 规范化（无项目 API 上下文时的轻量路径）。
 * <p>
 * 草稿覆盖后再跑一遍规范化，避免草稿冲掉先前补全的字段。
 * HTTP 此处只补 callMode、extracts、successCheck，不做相对接口资产的 overrides 差分。
 * 各类型规范化结束后，对允许写出的 alwaysRun、snapshotBefore 把常见脏值收成真正布尔。
 */
public final class FlowDesignNodeDataNormalizer {

    private FlowDesignNodeDataNormalizer() {
    }

    /**
     * 按 type 调用对应节点规范化，再统一收 alwaysRun / snapshotBefore 布尔脏值。
     *
     * @param type 节点 type
     * @param data 节点 data，就地修改；null 则直接返回
     */
    public static void normalize(String type, Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String t = type != null ? type.trim().toLowerCase(Locale.ROOT) : "";
        switch (t) {
            case "http" -> FlowDesignHttpNodeNormalizer.normalizeWithoutApi(data);
            case "script" -> FlowDesignScriptNodeNormalizer.normalize(data);
            case "assert" -> FlowDesignAssertNodeNormalizer.normalize(data);
            case "condition" -> FlowDesignConditionNodeNormalizer.normalize(data);
            case "assign" -> FlowDesignAssignNodeNormalizer.normalize(data);
            case "delay" -> FlowDesignDelayNodeNormalizer.normalize(data);
            case "subflow" -> FlowDesignSubflowNodeNormalizer.normalize(data);
            default -> {
            }
        }
        // 允许类型上的 alwaysRun / snapshotBefore：字符串或 0/1 等收成 Boolean
        GraphNodeBooleanFieldRules.coerceForNodeType(t, data);
    }
}
