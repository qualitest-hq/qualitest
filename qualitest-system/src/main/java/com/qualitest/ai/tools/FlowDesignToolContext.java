package com.qualitest.ai.tools;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.flow.model.GraphJson;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 测试流 AI 设计工具执行时的请求级上下文。
 * <p>
 * Web「AI 设计」与 MCP 网关共用本对象：承载项目、测试流、画布、检索范围及运行时限制。
 * MCP 侧通常在 {@code get_flow} 后将 graphJson 填入本对象，再调用依赖画布的工具。
 */
@Getter
@Builder
public class FlowDesignToolContext {

    /** 当前测试项目 id，用于 API 归属校验与项目内检索 */
    private final Long testProjectId;

    /** 当前测试流 id，用于 Run 失败查询时的归属校验 */
    private final Long testFlowId;

    /**
     * 当前画布 graph_json。
     * Web 由设计面板传入；MCP 通常由 {@code get_flow} 取得后写入请求信封。
     * 供图摘要、节点详情读取；submit 时作为预合并与校验的基准图。
     */
    private final GraphJson graphJson;

    /**
     * 用户在面板 @ 选中的 API id 列表。
     * {@code search_apis} 优先在此范围内匹配。
     */
    private final List<Long> scopeApiIds;

    /**
     * 用户在画布选中的节点 id 列表。
     * 提示模型可对相关节点调用 {@code get_node_detail}。
     */
    private final List<String> contextNodeIds;

    /**
     * 从 Run 详情「AI 修复」入口传入的失败 Run id。
     * {@code get_run_failure} 在未显式传 runId 时回退使用此值。
     */
    private final Long contextRunId;

    /** {@code search_apis} 单次返回的最大条数 */
    @Builder.Default
    private final int maxSearchApis = AiLlmConfigService.DEFAULT_MAX_SEARCH_APIS;

    /** {@code list_flows} / {@code list_subflow_templates} 单次返回的最大条数 */
    @Builder.Default
    private final int maxListFlows = AiLlmConfigService.DEFAULT_MAX_LIST_FLOWS;

    /** 只读工具返回 JSON 的字节上限；超出时附 {@code truncated} 与 {@code hint} */
    @Builder.Default
    private final int maxToolResultBytes = AiLlmConfigService.DEFAULT_MAX_TOOL_RESULT_BYTES;

    /**
     * 本轮 submit 结果捕获器。
     * <p>
     * 由编排层每轮请求新建并注入；{@code submit_flow_design_patch} 执行后写入规范化 patch。
     * 可为 null（仅影响 capture 写入，不影响 submit 本身的校验与 tool_result 返回）。
     */
    private final FlowDesignSubmitCapture submitCapture;
}
