package com.qualitest.flow.validate;

import lombok.Builder;
import lombok.Getter;

/**
 * 流图上的一条诊断问题。
 * 用稳定诊断码区分种类；严重性决定是否硬拦开跑；阶段标明面向画布、保存还是开跑等门禁。
 */
@Getter
@Builder
public class FlowIssue {

    /** 诊断码：核心短码，或 {@code 插件id/短码} */
    private final String code;

    /**
     * 运行时严重性。
     * ERROR 会硬拦开跑；WARNING 只提示。
     * 配置层的 OFF 不会进入本对象，在定级阶段已被过滤。
     */
    private final Severity severity;

    /** 该问题所属门禁阶段 */
    private final Stage stage;

    /** 关联节点 id；与节点无关时可为 null */
    private final String nodeId;

    /** 给人看的说明文案 */
    private final String message;

    /** 问题严重性：错误或警告 */
    public enum Severity {
        ERROR,
        WARNING
    }

    /**
     * 校验 / 门禁阶段。
     * 查询参数可用 {@link #fromQuery(String)} / {@link #toQuery()} 互转。
     */
    public enum Stage {
        CANVAS,
        SAVE,
        RUN,
        INGRESS,
        SUBFLOW_CALL;

        /** 解析查询参数；空白或无法识别时返回 null */
        public static Stage fromQuery(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String key = raw.trim().toLowerCase();
            return switch (key) {
                case "canvas" -> CANVAS;
                case "save" -> SAVE;
                case "run" -> RUN;
                case "ingress" -> INGRESS;
                case "subflow", "subflow_call" -> SUBFLOW_CALL;
                default -> null;
            };
        }

        /** 写成查询参数字符串 */
        public String toQuery() {
            return switch (this) {
                case CANVAS -> "canvas";
                case SAVE -> "save";
                case RUN -> "run";
                case INGRESS -> "ingress";
                case SUBFLOW_CALL -> "subflow";
            };
        }
    }

    /** 是否为错误级 */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** 是否为警告级 */
    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    /** 投影为旧版校验问题结构，便于沿用现有展示 */
    public ValidationIssue toValidationIssue() {
        ValidationIssue.Level level = isWarning() ? ValidationIssue.Level.WARNING : ValidationIssue.Level.ERROR;
        return new ValidationIssue(level, message);
    }

    /** 构造一条错误级问题 */
    public static FlowIssue error(String code, Stage stage, String nodeId, String message) {
        return FlowIssue.builder()
                .code(code)
                .severity(Severity.ERROR)
                .stage(stage)
                .nodeId(nodeId)
                .message(message)
                .build();
    }

    /** 构造一条警告级问题 */
    public static FlowIssue warning(String code, Stage stage, String nodeId, String message) {
        return FlowIssue.builder()
                .code(code)
                .severity(Severity.WARNING)
                .stage(stage)
                .nodeId(nodeId)
                .message(message)
                .build();
    }
}
