package com.qualitest.ai.scenario.flow;

import java.util.Locale;
import java.util.Map;

/**
 * 按节点 type 分发 data 规范化（无 API 上下文时的轻量路径）。
 * <p>
 * 供 Staging confirm 在 draft 覆盖后再跑一遍，避免用户草稿冲掉 preparePatch 的补全。
 * HTTP 此处仅补 callMode / extracts / successCheck，不依赖项目 API 做 overrides 差分。
 */
public final class FlowDesignNodeDataNormalizer {

    private FlowDesignNodeDataNormalizer() {
    }

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
    }
}
