package com.qualitest.ai.scenario.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 提交节点时 data 字段白名单。
 * <p>
 * 规范化阶段删除不在名单内的键，并把删除的键名记入 warning，
 * 防止模型臆造字段进入 Staging 预览。
 * 未知节点类型返回空名单，不做剔除。
 */
public final class FlowDesignNodeDataKeys {

    /** 各类型共用：显示名与画布副标题 */
    private static final Set<String> COMMON = Set.of("name", "summary");

    /**
     * HTTP 节点 data 允许键。
     * extractsOnFailure：步骤失败时是否仍执行 extracts（skip=默认不写；write=失败也写内存抽取）。
     * statusCheck：按 HTTP 状态码判定节点成败；successCheck：按 body 业务码判定。
     */
    private static final Set<String> HTTP = Set.of(
            "callMode", "testProjectApiId", "externalUrl", "httpMethod",
            "headers", "requestBody", "requestValueOverrides", "extracts", "extractsOnFailure",
            "statusCheck", "successCheck", "timeoutMs", "preScript", "postScript",
            "apiName", "apiPath", "authProfileId");

    /** 断言节点 data 允许键 */
    private static final Set<String> ASSERT = Set.of("rules");

    /** 条件分支节点 data 允许键 */
    private static final Set<String> CONDITION = Set.of("branches");

    /** 赋值节点 data 允许键 */
    private static final Set<String> ASSIGN = Set.of("assignments");

    /** 延时节点 data 允许键 */
    private static final Set<String> DELAY = Set.of("ms");

    /** 脚本节点 data 允许键 */
    private static final Set<String> SCRIPT = Set.of("language", "source");

    /** 子流节点 data 允许键（含 outputs 供生成副标题） */
    private static final Set<String> SUBFLOW = Set.of(
            "subflowId", "subflowName", "versionPolicy", "flowOutputs", "outputs", "graphJson");

    private FlowDesignNodeDataKeys() {
    }

    /**
     * 返回指定节点类型允许的 data 键集合（已含 name、summary）。
     * 无法识别的 type 返回空集合。
     */
    public static Set<String> allowedFor(String type) {
        String t = type != null ? type.trim().toLowerCase(Locale.ROOT) : "";
        Set<String> keys = new LinkedHashSet<>(COMMON);
        switch (t) {
            case "http" -> keys.addAll(HTTP);
            case "assert" -> keys.addAll(ASSERT);
            case "condition" -> keys.addAll(CONDITION);
            case "assign" -> keys.addAll(ASSIGN);
            case "delay" -> keys.addAll(DELAY);
            case "script" -> keys.addAll(SCRIPT);
            case "subflow" -> keys.addAll(SUBFLOW);
            default -> {
                return Collections.emptySet();
            }
        }
        return Set.copyOf(keys);
    }

    /**
     * 原地删除 data 中不在白名单的键；空白键名一并删除。
     *
     * @return 被删除的键名列表（保持遍历顺序）；未删除时返回空列表
     */
    public static List<String> stripUnknown(String type, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = allowedFor(type);
        if (allowed.isEmpty()) {
            return List.of();
        }
        List<String> removed = new ArrayList<>();
        Iterator<Map.Entry<String, Object>> it = data.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            String key = e.getKey();
            if (key == null || key.isBlank()) {
                it.remove();
                continue;
            }
            if (!allowed.contains(key)) {
                removed.add(key);
                it.remove();
            }
        }
        return removed;
    }
}
