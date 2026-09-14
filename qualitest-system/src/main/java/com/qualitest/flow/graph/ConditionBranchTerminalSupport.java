package com.qualitest.flow.graph;

import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * 条件分支「是否结束本流」判定。
 * <p>
 * 有非空 target：命中后走到下游节点。<br>
 * 无非空 target：命中后本流正常结束。
 */
public final class ConditionBranchTerminalSupport {

    private ConditionBranchTerminalSupport() {
    }

    /**
     * 分支是否配置了有效下游 target。
     * 空串、空白、字面量 {@code "null"} 视为无效。
     */
    public static boolean hasBranchTarget(Map<?, ?> branch) {
        if (branch == null) {
            return false;
        }
        Object targetObj = branch.get("target");
        if (targetObj == null) {
            return false;
        }
        String target = String.valueOf(targetObj).trim();
        return !target.isEmpty() && !"null".equalsIgnoreCase(target);
    }

    /**
     * 是否为结束分支：命中后不再走向下一节点。
     * 判定依据是「没有有效 target」。
     */
    public static boolean isTerminalBranch(Map<?, ?> branch) {
        return !hasBranchTarget(branch);
    }

    /** JSONObject 入参的结束分支判定。 */
    public static boolean isTerminalBranch(JSONObject branch) {
        return isTerminalBranch((Map<?, ?>) branch);
    }
}
