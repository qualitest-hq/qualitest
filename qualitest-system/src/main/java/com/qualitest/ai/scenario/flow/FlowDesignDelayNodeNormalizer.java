package com.qualitest.ai.scenario.flow;

import com.qualitest.flow.delay.DelayConstants;

import java.util.Map;

/**
 * AI / 设计 patch 落图前，规范化 delay 节点 {@code data.ms}。
 * 空缺 → {@link DelayConstants#DEFAULT_DELAY_MS}；负值归零；超过上限截断。
 */
public final class FlowDesignDelayNodeNormalizer {

    private FlowDesignDelayNodeNormalizer() {
    }

    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        data.put("ms", DelayConstants.normalizeMsForDesign(data.get("ms")));
    }
}
