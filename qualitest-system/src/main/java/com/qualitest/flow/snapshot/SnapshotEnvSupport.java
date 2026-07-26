package com.qualitest.flow.snapshot;

import com.qualitest.flow.context.ResetEndpointSupport;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.project.domain.TestProjectEnv;

/**
 * 测试环境上的快照/还原护栏。
 * <p>
 * 读取环境是否允许对被测库做破坏性操作，以及从 envUrl 解析被测方 test-support 根路径。
 * 环境未开启 allowDestructiveReset 时，checkpoint 与 restore 均静默跳过，不调用被测端点、不写审计步。
 */
public final class SnapshotEnvSupport {

    private SnapshotEnvSupport() {
    }

    /**
     * 环境是否允许打快照/做还原。
     * allowDestructiveReset 为 1 时返回 true；null、0 或环境不存在时返回 false。
     */
    public static boolean isResetAllowed(TestProjectEnv env) {
        if (env == null) {
            return false;
        }
        Integer v = env.getAllowDestructiveReset();
        return v != null && v == 1;
    }

    /**
     * 从环境 envUrl 派生 test-support 根路径；无法派生时返回空字符串。
     */
    public static String resolveResetBase(TestProjectEnv env) {
        if (env == null || env.getEnvUrl() == null) {
            return "";
        }
        return ResetEndpointSupport.resolve(env.getEnvUrl());
    }

    /**
     * 解析 test-support 根路径；环境不存在或无法派生时抛错。
     */
    public static String requireResetBase(TestProjectEnv env) {
        if (env == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_SNAPSHOT_ENDPOINT, "环境不存在");
        }
        String base = resolveResetBase(env);
        if (base.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_SNAPSHOT_ENDPOINT, "无法从 envUrl 派生 reset 端点");
        }
        return base;
    }
}
