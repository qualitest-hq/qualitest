package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.config.AiLlmConfigService;
import lombok.Builder;
import lombok.Getter;

/**
 * 单轮工具执行上下文：当前项目、当前 API、编辑器脚本草稿、submit 捕获器等。
 */
@Getter
@Builder
public class ApiDesignToolContext {

    /** 测试项目 id */
    private final Long testProjectId;

    /** 当前接口 id */
    private final Long testProjectApiId;

    /** 前端传入的前置脚本草稿 */
    private final String preRequestScript;

    /** 前端传入的后置脚本草稿 */
    private final String postRequestScript;

    /** 可选工作台草稿摘要；有则在 context 工具中回显给模型 */
    private final String workbenchSnapshot;

    /**
     * 本轮 submit_api_design_patch 的捕获器。
     * Agent 据此判断是否产出了结构化 patch。
     */
    private final ApiDesignSubmitCapture submitCapture;

    /**
     * 是否开启全自动（请求级）。
     * 影响 submit 回执 hint 与 system 附加规程；真正 apply 在前端。
     */
    @Builder.Default
    private final boolean autopilotEnabled = false;

    /** 工具返回 JSON 的最大字节数，超限会被截断 */
    @Builder.Default
    private final int maxToolResultBytes = AiLlmConfigService.DEFAULT_MAX_TOOL_RESULT_BYTES;
}
