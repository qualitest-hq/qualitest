package com.qualitest.plugin.invoke;

import lombok.Builder;
import lombok.Getter;

/**
 * 插件调用结果：封装本机 CLI 进程输出。
 */
@Getter
@Builder
public class PluginInvokeResult {

    /** 是否按约定成功结束（退出码为 0） */
    private final boolean ok;
    /** 标准输出原文 */
    private final String stdout;
    /** 标准错误原文 */
    private final String stderr;
    /** 进程退出码 */
    private final int exitCode;
    /**
     * 供上层解析的 JSON 文本。
     * 当前实现直接取标准输出；CLI 约定成功时 stdout 为 JSON。
     */
    private final String parsedJson;
}
