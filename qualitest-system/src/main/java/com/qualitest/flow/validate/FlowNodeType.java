package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphNode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流画布节点 type 枚举（持久化小写 code）。
 * <ul>
 *   <li>HTTP — 调 HTTP；data.callMode 为 project（项目接口）或 external（外联 URL）</li>
 *   <li>ASSERT — 对上下文做比较断言</li>
 *   <li>CONDITION — IF / ELIF / ELSE 分支</li>
 *   <li>ASSIGN — 写入 flow 或 out 变量</li>
 *   <li>DELAY — 等待</li>
 *   <li>SCRIPT — GraalVM 沙箱脚本</li>
 *   <li>SUBFLOW — 引用同项目另一张测试流；子交付挂本节点 out</li>
 *   <li>INPUT — 暂停等待人工填写</li>
 *   <li>HTTP_RETURN — 开放图返回：定稿 out 并立刻写出；默认不结束路径</li>
 *   <li>SUBFLOW_RETURN — 子流图返回：定稿 out 并立刻挂父节点；默认不结束路径</li>
 *   <li>SCENARIO_ENTRY — 场景图入口；引擎透传；场景图不画返回节点</li>
 *   <li>SUBFLOW_ENTRY — 子流图入口；须配置 fallbackOut</li>
 *   <li>HTTP_ENTRY — 开放 HTTP 入口（method/path/envId/fallbackOut）；引擎透传</li>
 *   <li>PLUGIN_ACTION — 平台插件步骤（引擎侧只有这一种类型，具体外观与能力由插件声明）</li>
 * </ul>
 * 多出边（非 condition）由运行器隐式扇出，不设独立节点类型。
 * 协议入口：持久化为 {@code {protocol}-entry}（如 tcp-entry），枚举中不逐一列出；
 * 是否已知类型、是否入口类型均按 {@code -entry} 后缀规则识别。
 */
@Getter
public enum FlowNodeType {

    /** HTTP 请求 */
    HTTP("http", "HTTP"),
    /** 断言 */
    ASSERT("assert", "Assert"),
    /** 延时等待 */
    DELAY("delay", "Delay"),
    /** 条件分支 */
    CONDITION("condition", "Condition"),
    /** 变量赋值 */
    ASSIGN("assign", "Assign"),
    /** 脚本 */
    SCRIPT("script", "Script"),
    /** 子流调用 */
    SUBFLOW("subflow", "Subflow"),
    /** 人工输入 */
    INPUT("input", "Input"),
    /** 平台插件步骤：调用已启用插件提供的能力 */
    PLUGIN_ACTION("pluginAction", "Plugin Action"),
    /** 开放 HTTP 定稿写出 */
    HTTP_RETURN("http-return", "HTTP Return"),
    /** 子流定稿挂父 */
    SUBFLOW_RETURN("subflow-return", "Subflow Return"),
    /** 场景主图入口 */
    SCENARIO_ENTRY("scenario-entry", "Scenario Entry"),
    /** 子流图入口 */
    SUBFLOW_ENTRY("subflow-entry", "Subflow Entry"),
    /** 开放 HTTP 图入口 */
    HTTP_ENTRY("http-entry", "HTTP Entry");

    /** code 到枚举的映射 */
    private static final Map<String, FlowNodeType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(FlowNodeType::getCode, t -> t));

    /** 全部已枚举的 type code 集合 */
    private static final Set<String> KNOWN_CODES = Arrays.stream(values())
            .map(FlowNodeType::getCode)
            .collect(Collectors.toUnmodifiableSet());

    /** 三种平台入口的 type code 集合 */
    private static final Set<String> ENTRY_CODES = Set.of(
            SCENARIO_ENTRY.code, SUBFLOW_ENTRY.code, HTTP_ENTRY.code);

    /** 返回节点的 type code 集合 */
    private static final Set<String> RETURN_CODES = Set.of(
            HTTP_RETURN.code, SUBFLOW_RETURN.code);

    /** 协议入口 type：小写字母开头、仅字母数字、以 -entry 结尾 */
    private static final java.util.regex.Pattern PROTOCOL_ENTRY =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9]*-entry$");

    /** 持久化与画布使用的节点 type 字符串 */
    private final String code;

    /** 画布与报告展示的英文标签 */
    private final String label;

    /** 构造节点类型枚举项：code 为持久化字符串，label 为展示标签 */
    FlowNodeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 判断是否与给定节点 type 字符串相同 */
    public boolean matches(String type) {
        return code.equals(type);
    }

    /** 由持久化 code 解析枚举；未知或空串返回 empty */
    public static Optional<FlowNodeType> fromCode(String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(type));
    }

    /** 是否为已知可执行 type（含平台枚举与协议入口 {protocol}-entry） */
    public static boolean isKnown(String type) {
        if (type == null) {
            return false;
        }
        return KNOWN_CODES.contains(type) || isProtocolEntry(type);
    }

    /**
     * 是否为入口节点 type（三种平台入口 + 协议入口）。
     * 有入口时图校验要求入口无入边，且仅入口可作为开跑起点。
     */
    public static boolean isEntry(String type) {
        return type != null && (ENTRY_CODES.contains(type) || isProtocolEntry(type));
    }

    /**
     * 是否为插件贡献的协议入口（如 {@code tcp-entry}）。
     * 三种平台入口不算协议入口。
     */
    public static boolean isProtocolEntry(String type) {
        if (type == null || ENTRY_CODES.contains(type)) {
            return false;
        }
        return PROTOCOL_ENTRY.matcher(type).matches();
    }

    /**
     * 协议入口短名：{@code tcp-entry} → {@code tcp}；非协议入口返回 null。
     */
    public static String protocolShortName(String type) {
        if (!isProtocolEntry(type)) {
            return null;
        }
        return type.substring(0, type.length() - "-entry".length());
    }

    /**
     * 是否为返回节点 type：仅 {@code http-return}、{@code subflow-return}。
     * 裸字符串 {@code return} 不在已知集合内，保存校验会按未知 type 拦下。
     */
    public static boolean isReturn(String type) {
        return type != null && RETURN_CODES.contains(type);
    }

    /** 取展示标签；未知 type 原样返回 */
    public static String labelOf(String type) {
        return fromCode(type)
                .map(FlowNodeType::getLabel)
                .filter(label -> !label.isBlank())
                .orElse(type);
    }
}
