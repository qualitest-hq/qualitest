package com.qualitest.plugin.invoke;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 插件调用入参：指定在哪个环境调用哪个插件的哪项能力。
 */
@Getter
@Builder
public class PluginInvokeRequest {

    /** 项目 ID；为空时由调用方用环境所属项目补齐 */
    private final Long projectId;
    /** 测试环境 ID（必填） */
    private final Long envId;
    /** 测流运行 ID；写入 CLI 配置，便于存档点按运行区分 */
    private final Long runId;
    /** 插件标识（必填） */
    private final String pluginId;
    /** 能力名，须在说明书 capabilities 内（必填） */
    private final String capability;
    /** 能力参数，如 saveId、storeKey、force */
    private final Map<String, Object> args;
    /** 调用来源标记，写入审计日志，如 onCheckpoint:节点标签 */
    private final String source;
}
