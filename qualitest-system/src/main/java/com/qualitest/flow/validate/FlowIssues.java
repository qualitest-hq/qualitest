package com.qualitest.flow.validate;

import java.util.ArrayList;
import java.util.List;

/**
 * FlowIssue 组装辅助：把结构校验 errors 投影为结构化诊断。
 */
public final class FlowIssues {

    private FlowIssues() {}

    /**
     * 将 {@link GraphValidationResult} 的 errors 投影为 FlowIssue 列表。
     *
     * @param result 结构校验结果
     * @param stage  诊断阶段
     */
    public static List<FlowIssue> fromValidationResult(GraphValidationResult result, FlowIssue.Stage stage) {
        List<FlowIssue> issues = new ArrayList<>();
        if (result == null) {
            return issues;
        }
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.CANVAS;
        if (result.getErrors() != null) {
            for (String err : result.getErrors()) {
                if (err == null || err.isBlank()) {
                    continue;
                }
                issues.add(FlowIssue.error(
                        FlowIssueCodes.GRAPH_STRUCTURE, effective, guessNodeId(err), err));
            }
        }
        return issues;
    }

    /**
     * 从常见文案中尽量抽出节点 id（如 {@code nodes[n1]} / 「id」形态）；抽不到返回 null。
     */
    static String guessNodeId(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        // nodes[<id>] 缺少 …
        int nodesIdx = message.indexOf("nodes[");
        if (nodesIdx >= 0) {
            int start = nodesIdx + "nodes[".length();
            int end = message.indexOf(']', start);
            if (end > start) {
                String token = message.substring(start, end).trim();
                // 数字下标不是节点 id
                if (!token.isEmpty() && !token.chars().allMatch(Character::isDigit)) {
                    return token;
                }
            }
        }
        return null;
    }
}
