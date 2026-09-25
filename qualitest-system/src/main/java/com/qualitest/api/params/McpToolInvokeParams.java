package com.qualitest.api.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.flow.model.GraphJson;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * MCP 单次工具调用的请求参数。
 * <p>
 * 由协议层将 {@code tools/call} 的 {@code arguments} 拆成两部分：
 * <ul>
 *   <li>信封字段（本类顶层属性）：项目、测试流、画布、检索范围等，可在连续多次调用间复用</li>
 *   <li>{@link #arguments}：仅属于当前工具的业务入参，序列化后交给具体 Tool 实现</li>
 * </ul>
 * 典型流程：{@code list_flows} → {@code get_flow} 取得 graphJson 填入信封 → 再调依赖画布的
 * {@code get_graph_summary}、{@code get_flow_meta} 等。
 */
@Getter
@Setter
public class McpToolInvokeParams {

    /**
     * 测试项目 id。
     * 省略时使用 Project Token 绑定的项目；显式传入时须与 Token 归属项目匹配，否则拒绝请求。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 当前关注的测试流 id。
     * {@code get_flow} 在未传 arguments.testFlowId 时可回退使用此值。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /**
     * 测试流画布 graph_json。
     * {@code get_graph_summary}、{@code get_flow_meta}、{@code get_node_detail} 等只读工具依赖此字段；
     * 一般由 {@code get_flow} 的返回结果填入。
     */
    private GraphJson graphJson;

    /**
     * 画布图版本号。
     * 读出为库中当前值；写图时作为条件更新的基准版本。
     */
    private Long graphRevision;

    /**
     * API id 列表，限制 {@code search_apis} 的检索范围。
     */
    private List<String> scopeApiIds;

    /**
     * 画布节点 id 列表。
     * {@code get_graph_summary} 在节点过多压缩时仍返回这些节点的摘要。
     */
    private List<String> contextNodeIds;

    /**
     * 测试流 Run id。
     * {@code get_run_failure} 在未传 arguments.runId 时可回退使用此值。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long contextRunId;

    /**
     * 当前工具的业务参数，JSON 对象，字段因工具而异。
     * 例如 {@code search_apis} 传 {@code {"keyword":"login"}}。
     */
    private Map<String, Object> arguments;

    /** 将 {@link #arguments} 序列化为 JSON 字符串，传入 {@link com.qualitest.ai.tools.FlowDesignToolExecutor}。 */
    public String toArgumentsJson() {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        return com.alibaba.fastjson2.JSON.toJSONString(arguments);
    }
}
