package com.qualitest.flow.validate;

import lombok.Builder;
import lombok.Getter;

/**
 * 流图诊断单条问题：稳定 code + 严重级别 + 适用阶段，文案可改不影响契约测试。
 */
@Getter
@Builder
public class FlowIssue {

    /** 诊断码，如 ENTRY_ROUTE_CONFLICT */
    private final String code;

    /** 仅 error；不确定项静默不产出 issue */
    private final Severity severity;

    /** 该问题面向的门禁阶段 */
    private final Stage stage;

    /** 关联节点 id；可空 */
    private final String nodeId;

    /** 人类可读说明 */
    private final String message;

    public enum Severity {
        ERROR
    }

    /**
     * 与未来 FlowGate.Stage / validate?stage= 同名同义。
     */
    public enum Stage {
        CANVAS,
        SAVE,
        RUN,
        INGRESS,
        SUBFLOW_CALL;

        /** 解析查询参数；非法返回 null */
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

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** 兼容旧 ValidationIssue 投影 */
    public ValidationIssue toValidationIssue() {
        return new ValidationIssue(ValidationIssue.Level.ERROR, message);
    }

    public static FlowIssue error(String code, Stage stage, String nodeId, String message) {
        return FlowIssue.builder()
                .code(code)
                .severity(Severity.ERROR)
                .stage(stage)
                .nodeId(nodeId)
                .message(message)
                .build();
    }
}
