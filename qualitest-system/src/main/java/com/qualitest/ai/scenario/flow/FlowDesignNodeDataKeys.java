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
 * 规范化阶段删除不在名单内的键，并把删除的键名记入警告，
 * 避免臆造字段进入预览图。未知节点类型返回空名单，不做剔除。
 */
public final class FlowDesignNodeDataKeys {

    /** 展示名写在节点顶层；data 白名单仅含各 type 业务键 */
    private static final Set<String> COMMON = Set.of();

    /**
     * HTTP 节点 data 允许键。
     * extractsOnFailure：步骤失败时是否仍执行抽取（skip 默认不写，write 失败也写内存）。
     * statusCheck：按 HTTP 状态码判定成败；successCheck：按业务码判定。
     * alwaysRun：业务失败后继续阶段是否仍执行本节点。
     * authProfileId：本节点覆盖使用的多端鉴权配置 id。
     * retryUntil：重试直到通过——最多请求次数、间隔毫秒、可选通过条件规则。
     */
    private static final Set<String> HTTP = Set.of(
            "callMode", "testProjectApiId", "externalUrl", "httpMethod",
            "headers", "requestBody", "requestValueOverrides", "extracts", "extractsOnFailure",
            "statusCheck", "successCheck", "timeoutMs", "preScript", "postScript",
            "apiName", "apiPath", "authProfileId", "alwaysRun", "retryUntil");

    /** 断言节点 data 允许键 */
    private static final Set<String> ASSERT = Set.of("rules");

    /** 条件分支节点 data 允许键 */
    private static final Set<String> CONDITION = Set.of("branches");

    /**
     * 赋值节点 data 允许键。
     * alwaysRun：业务失败后继续阶段是否仍执行本节点。
     */
    private static final Set<String> ASSIGN = Set.of("assignments", "alwaysRun");

    /** 延时节点 data 允许键 */
    private static final Set<String> DELAY = Set.of("ms");

    /**
     * 脚本节点 data 允许键。
     * alwaysRun：业务失败后继续阶段是否仍执行本节点。
     */
    private static final Set<String> SCRIPT = Set.of("language", "source", "alwaysRun");

    /**
     * 子流节点 data 允许键。
     * inputs：父流程向子流程注入的 in/flow 种子；
     * alwaysRun：业务失败后继续阶段是否仍执行本子流节点；
     * 子图定稿出参由子图内 subflow-return（或入口 fallbackOut）挂父 nodes.&lt;id&gt;.out。
     */
    private static final Set<String> SUBFLOW = Set.of(
            "subflowId", "subflowName", "inputs", "alwaysRun", "templateId");

    /**
     * 人工输入节点 data 允许键。
     * fields：待填字段列表；alwaysRun：失败后继续阶段是否仍执行。
     */
    private static final Set<String> INPUT = Set.of("fields", "alwaysRun");

    /**
     * 插件步骤（pluginAction）data 允许键。
     * pluginId / capability 必填；contributionId 标识 palette 贡献；
     * config 为该贡献的步骤参数；alwaysRun 控制失败后继续阶段是否仍执行。
     */
    private static final Set<String> PLUGIN_ACTION = Set.of(
            "pluginId", "contributionId", "capability", "config", "alwaysRun");

    /** http-return：out 来源路径与可选 HTTP 状态码 */
    private static final Set<String> HTTP_RETURN = Set.of("from", "status");

    /** subflow-return：子流定稿 out 的来源路径 */
    private static final Set<String> SUBFLOW_RETURN = Set.of("from");

    /** 平台入口壳：单次 Run 步数上限（协议入口同样认此键，不另发明名） */
    private static final String ENTRY_MAX_STEPS = "maxSteps";

    /** scenario-entry：入口说明、步数上限、入参键提示与可批量执行的场景列表 */
    private static final Set<String> SCENARIO_ENTRY = Set.of(
            "doc", ENTRY_MAX_STEPS, "inSeedKeys", "scenarios");

    /** http-entry：开放路由 method/path、环境、步数上限与收束兜底出参 */
    private static final Set<String> HTTP_ENTRY = Set.of(
            "method", "path", "envId", "fallbackOut", "doc", "inputShape", ENTRY_MAX_STEPS);

    /** subflow-entry：扁平入参声明、兜底出参、说明与步数上限（不使用 HTTP 三节 inputShape） */
    private static final Set<String> SUBFLOW_ENTRY = Set.of(
            "fallbackOut", "doc", "inputDecl", ENTRY_MAX_STEPS);

    private FlowDesignNodeDataKeys() {
    }

    /**
     * 返回指定节点类型允许的 data 键集合。
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
            case "input" -> keys.addAll(INPUT);
            case "pluginaction" -> keys.addAll(PLUGIN_ACTION);
            case "http-return" -> keys.addAll(HTTP_RETURN);
            case "subflow-return" -> keys.addAll(SUBFLOW_RETURN);
            case "scenario-entry" -> keys.addAll(SCENARIO_ENTRY);
            case "http-entry" -> keys.addAll(HTTP_ENTRY);
            case "subflow-entry" -> keys.addAll(SUBFLOW_ENTRY);
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
