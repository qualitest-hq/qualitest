package com.qualitest.plugin.channel;

/**
 * 环境级执行通道种类（核心分发，非插件开放能力）。
 */
public enum ExecChannelKind {
    /** 质衡本机直接起业务 CLI */
    LOCAL_EXEC("local-exec"),
    /** 经已启用的执行代理代跑业务 CLI */
    EXEC_AGENT("exec-agent");

    private final String wire;

    ExecChannelKind(String wire) {
        this.wire = wire;
    }

    /** 配置与日志中使用的通道标识字符串。 */
    public String getWire() {
        return wire;
    }

    /**
     * 将配置中的通道字符串解析为枚举；空或无法识别时按本机通道处理。
     */
    public static ExecChannelKind fromWire(String raw) {
        if (raw == null || raw.isBlank() || LOCAL_EXEC.wire.equalsIgnoreCase(raw.trim())) {
            return LOCAL_EXEC;
        }
        if (EXEC_AGENT.wire.equalsIgnoreCase(raw.trim())) {
            return EXEC_AGENT;
        }
        return LOCAL_EXEC;
    }
}
