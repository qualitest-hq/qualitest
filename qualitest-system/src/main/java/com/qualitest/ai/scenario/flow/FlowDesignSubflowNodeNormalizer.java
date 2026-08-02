package com.qualitest.ai.scenario.flow;

import java.util.Map;

/**
 * AI / 设计 patch 落图前，规范化 subflow 节点：空 {@code versionPolicy} → {@code latest}。
 */
public final class FlowDesignSubflowNodeNormalizer {

    public static final String VERSION_LATEST = "latest";

    private FlowDesignSubflowNodeNormalizer() {
    }

    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object policy = data.get("versionPolicy");
        if (policy == null || String.valueOf(policy).trim().isEmpty()) {
            data.put("versionPolicy", VERSION_LATEST);
        }
    }
}
