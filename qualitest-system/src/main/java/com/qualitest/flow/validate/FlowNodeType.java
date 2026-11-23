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
 * </ul>
 */
@Getter
public enum FlowNodeType {

    HTTP("http", "HTTP"),
    ASSERT("assert", "Assert"),
    DELAY("delay", "Delay"),
    CONDITION("condition", "Condition"),
    ASSIGN("assign", "Assign"),
    SCRIPT("script", "Script"),
    SUBFLOW("subflow", "Subflow"),
    INPUT("input", "Input"),
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

    private static final Map<String, FlowNodeType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(FlowNodeType::getCode, t -> t));

    private static final Set<String> KNOWN_CODES = Arrays.stream(values())
            .map(FlowNodeType::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> ENTRY_CODES = Set.of(
            SCENARIO_ENTRY.code, SUBFLOW_ENTRY.code, HTTP_ENTRY.code);

    private static final Set<String> RETURN_CODES = Set.of(
            HTTP_RETURN.code, SUBFLOW_RETURN.code);

    /** 持久化与画布使用的节点 type 字符串 */
    private final String code;

    /** 画布与报告展示的英文标签 */
    private final String label;

    FlowNodeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 判断是否与给定 {@link GraphNode#getType()} 字符串相同 */
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

    /** 是否为已知可执行 type */
    public static boolean isKnown(String type) {
        return type != null && KNOWN_CODES.contains(type);
    }

    /**
     * 是否为入口节点 type（scenario-entry / subflow-entry / http-entry）。
     * 有入口时图校验要求入口无入边，且仅入口可作为开跑起点。
     */
    public static boolean isEntry(String type) {
        return type != null && ENTRY_CODES.contains(type);
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
