package com.qualitest.ai.tools;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.scenario.flow.AiDesignMentionSupport;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建 {@link FlowDesignToolContext} 的工厂。
 * <p>
 * Web「AI 设计」与 MCP 网关均通过本工厂组装上下文字段（项目 id、画布、检索范围、字节上限等）。
 */
@Component
@RequiredArgsConstructor
public class FlowDesignToolContextFactory {

    private final AiLlmConfigService aiLlmConfigService;

    /**
     * 从 Web 设计请求构建上下文。
     * <p>
     * 将用户 @ 引用（mentions）解析为 scopeApiIds、contextNodeIds、contextRunId；
     * submitCapture 由编排层注入，供 {@code submit_flow_design_patch} 写入规范化结果。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture) {
        AiDesignMentionSupport.ResolvedMentionContext resolved =
                AiDesignMentionSupport.resolve(request.getMentions());
        return build(
                request.getTestProjectId(),
                request.getTestFlowId(),
                request.getGraphJson(),
                emptyToNull(resolved.getScopeApiIds()),
                emptyToNull(resolved.getContextNodeIds()),
                resolved.getContextRunId(),
                submitCapture);
    }

    /**
     * 从 MCP 调用请求构建上下文。
     * <p>
     * 项目 id 优先取 params.testProjectId，否则使用 Token 解析结果。
     * MCP 只读工具不需要 submitCapture。
     *
     * @param tokenProjectId Project Token 过滤器写入的项目 id
     */
    public FlowDesignToolContext fromMcpRequest(McpToolInvokeParams params, Long tokenProjectId) {
        return fromMcpRequest(params, tokenProjectId, null);
    }

    /**
     * 从 MCP 调用请求构建上下文（可注入 submitCapture，一般 MCP 路径使用无参重载）。
     */
    public FlowDesignToolContext fromMcpRequest(McpToolInvokeParams params,
                                                Long tokenProjectId,
                                                FlowDesignSubmitCapture submitCapture) {
        Long projectId = params.getTestProjectId() != null ? params.getTestProjectId() : tokenProjectId;
        if (projectId == null) {
            throw new ServiceException("缺少 testProjectId");
        }
        if (tokenProjectId != null && !tokenIdEquals(projectId, tokenProjectId)) {
            throw new ServiceException("testProjectId 与 Project Token 所属项目不一致");
        }
        return build(
                projectId,
                params.getTestFlowId(),
                params.getGraphJson(),
                parseLongIds(params.getScopeApiIds()),
                emptyToNull(params.getContextNodeIds()),
                params.getContextRunId(),
                submitCapture);
    }

    /**
     * 组装完整工具上下文，注入运行时限制参数。
     */
    private FlowDesignToolContext build(Long testProjectId,
                                        Long testFlowId,
                                        GraphJson graphJson,
                                        List<Long> scopeApiIds,
                                        List<String> contextNodeIds,
                                        Long contextRunId,
                                        FlowDesignSubmitCapture submitCapture) {
        return FlowDesignToolContext.builder()
                .testProjectId(testProjectId)
                .testFlowId(testFlowId)
                .graphJson(graphJson)
                .scopeApiIds(scopeApiIds)
                .contextNodeIds(contextNodeIds)
                .contextRunId(contextRunId)
                .maxSearchApis(aiLlmConfigService.getMaxSearchApis())
                .maxListFlows(aiLlmConfigService.getMaxListFlows())
                .maxToolResultBytes(aiLlmConfigService.getMaxToolResultBytes())
                .submitCapture(submitCapture)
                .build();
    }

    private static boolean tokenIdEquals(Long a, Long b) {
        return a != null && b != null && a.longValue() == b.longValue();
    }

    /** 空列表转为 null，工具层用 null 表示「未限制范围」 */
    private static <T> List<T> emptyToNull(List<T> list) {
        return list == null || list.isEmpty() ? null : list;
    }

    /** 将字符串 id 列表解析为 Long；非法项跳过 */
    private static List<Long> parseLongIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        List<Long> parsed = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException ignored) {
                // 跳过无法解析的 id
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }
}
