package com.qualitest.flow.graph;

import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * condition 分支「结束流程」({@code terminal: true}) 读写辅助。
 * <p>
 * terminal 分支命中后遍历器返回 null，子流正常结束；不可与 target 同时配置。
 */
public final class ConditionBranchTerminalSupport {

    private ConditionBranchTerminalSupport() {
    }

    public static boolean isTerminalBranch(Map<?, ?> branch) {
        if (branch == null) {
            return false;
        }
        Object terminal = branch.get("terminal");
        if (terminal instanceof Boolean b) {
            return b;
        }
        if (terminal != null) {
            return "true".equalsIgnoreCase(String.valueOf(terminal).trim());
        }
        return false;
    }

    public static boolean isTerminalBranch(JSONObject branch) {
        return isTerminalBranch((Map<?, ?>) branch);
    }
}
