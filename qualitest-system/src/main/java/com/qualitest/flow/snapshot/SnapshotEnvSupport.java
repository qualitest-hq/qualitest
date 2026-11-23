package com.qualitest.flow.snapshot;

import com.qualitest.plugin.PluginDestructiveGate;
import com.qualitest.project.domain.TestProjectEnv;

/**
 * 测试环境上的快照/还原护栏。
 * <p>
 * 主路径看环境是否允许破坏性还原（allowDestructiveReset）。
 * 未开启时 checkpoint 静默跳过；显式「还原并重试」由 SnapshotRestoreService 失败拒绝，不静默续跑。
 */
public final class SnapshotEnvSupport {

    private SnapshotEnvSupport() {
    }

    /**
     * 环境是否允许打快照/做还原。
     * allowDestructiveReset 为 1 时返回 true；null、0 或环境不存在时返回 false。
     */
    public static boolean isResetAllowed(TestProjectEnv env) {
        return PluginDestructiveGate.isAllowed(env);
    }
}
