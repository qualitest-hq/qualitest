package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphNode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流画布节点 type 枚举（固定 7 种，不支持用户自定义 type）。
 * <ul>
 *   <li>{@link #HTTP} — 调 HTTP；{@code data.callMode} 为 {@code project}（项目接口）或 {@code external}（外联 URL）</li>
 *   <li>{@link #ASSERT} — 对上一步 HTTP 响应断言</li>
 *   <li>{@link #CONDITION} — IF / ELIF / ELSE 分支</li>
 *   <li>{@link #ASSIGN} — 写入 {@code flow} 变量</li>
 *   <li>{@link #DELAY} — 等待</li>
 *   <li>{@link #SCRIPT} — GraalVM 沙箱脚本；可读写 {@code flow}、{@code session}，可读 {@code env}/{@code asset}，可选 {@code ctx.http}</li>
 *   <li>{@link #SUBFLOW} — 引用同项目另一张测试流图，折叠为单节点，经 inputs/outputs 与父 flow 交换变量</li>
 * </ul>
 * {@code code} 对应 {@link GraphNode#getType()} 持久化值；{@code label} 用于校验消息与 UI 展示。
 */
@Getter
public enum FlowNodeType {

    HTTP("http", "HTTP"),
    ASSERT("assert", "Assert"),
    DELAY("delay", "Delay"),
    CONDITION("condition", "Condition"),
    ASSIGN("assign", "Assign"),
    SCRIPT("script", "Script"),
    SUBFLOW("subflow", "Subflow");

    private static final Map<String, FlowNodeType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(FlowNodeType::getCode, t -> t));

    private static final Set<String> KNOWN_CODES = Arrays.stream(values())
            .map(FlowNodeType::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final String code;

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

    /** 取展示标签；未知 type 原样返回 */
    public static String labelOf(String type) {
        return fromCode(type)
                .map(FlowNodeType::getLabel)
                .filter(label -> !label.isBlank())
                .orElse(type);
    }
}
