package com.qualitest.plugin;

import com.qualitest.project.domain.TestProjectEnv;

/**
 * 环境破坏性写总闸：allowDestructiveReset=1 时允许破坏性能力。
 */
public final class PluginDestructiveGate {

    private PluginDestructiveGate() {
    }

    /**
     * 环境是否允许破坏性写。
     * allowDestructiveReset 为 1 时返回 true；null、0 或环境不存在时返回 false。
     */
    public static boolean isAllowed(TestProjectEnv env) {
        if (env == null) {
            return false;
        }
        Integer v = env.getAllowDestructiveReset();
        return v != null && v == 1;
    }
}
