package com.qualitest.ai.scenario.flow;

import com.qualitest.flow.script.ScriptConstants;

import java.util.Locale;
import java.util.Map;

/**
 * AI / 设计 patch 落图前，规范化 script 节点字段。
 * <p>
 * 常见问题：模型只写了源码，漏写 {@code language}；UI 展示却按 JavaScript 默认渲染，
 * Staging 确认时图校验仍报「language 无效：(空)」。
 * 此处在 submit / Staging 确认规范化阶段补默认值并归一别名。
 */
public final class FlowDesignScriptNodeNormalizer {

    private FlowDesignScriptNodeNormalizer() {
    }

    /**
     * 规范化 script 节点 data：language、timeoutMs。
     * language 空或仅空白 → javascript；js / JavaScript 等别名 → javascript；
     * py / Python → python；其它原样保留（交给图校验报错）。
     */
    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        data.put("language", normalizeLanguage(data.get("language")));
        data.put("timeoutMs", ScriptConstants.normalizeTimeoutMs(data.get("timeoutMs")));
    }

    /**
     * 将 language 收成持久化取值 {@code javascript} / {@code python}；
     * 空缺时默认 javascript；无法识别的非空串原样返回。
     */
    public static String normalizeLanguage(Object raw) {
        if (raw == null) {
            return ScriptConstants.LANGUAGE_JAVASCRIPT;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return ScriptConstants.LANGUAGE_JAVASCRIPT;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if ("javascript".equals(lower)
                || "js".equals(lower)
                || "node".equals(lower)
                || "nodejs".equals(lower)) {
            return ScriptConstants.LANGUAGE_JAVASCRIPT;
        }
        if ("python".equals(lower) || "py".equals(lower) || "python3".equals(lower)) {
            return ScriptConstants.LANGUAGE_PYTHON;
        }
        return s;
    }
}
