package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphNode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流画布节点 type 枚举（固定 8 种，不支持用户自定义 type）。
 * <ul>
 *   <li>HTTP — 调 HTTP；data.callMode 为 project（项目接口）或 external（外联 URL）</li>
 *   <li>ASSERT — 对上下文做比较断言</li>
 *   <li>CONDITION — IF / ELIF / ELSE 分支</li>
 *   <li>ASSIGN — 写入 flow 变量（设计期定值，运行自动执行）</li>
 *   <li>DELAY — 等待</li>
 *   <li>SCRIPT — GraalVM 沙箱脚本；可读写 flow、session，可读 env/asset，可选受控 HTTP</li>
 *   <li>SUBFLOW — 引用同项目另一张测试流，折叠为单节点，经 inputs/outputs 交换变量</li>
 *   <li>INPUT — 暂停等待人工填写，校验后写入 flow 再继续</li>
 * </ul>
 * code 为图 JSON 中 nodes[].type 持久化值；label 用于校验消息与 UI。
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
    /** 人工输入：运行暂停，人填后写入 flow */
    INPUT("input", "Input");

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
