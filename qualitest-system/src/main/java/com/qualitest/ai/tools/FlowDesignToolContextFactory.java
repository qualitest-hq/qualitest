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
     * 从 Web 设计请求构建工具上下文（不注入素材提案捕获器）。
     * 解析 mentions 得到检索范围与上下文节点 / Run；submitCapture 由调用方传入。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture) {
        return fromDesignRequest(request, submitCapture, null);
    }

    /**
     * 从 Web 设计请求构建工具上下文，并注入画布 patch 捕获器与素材提案捕获器。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture) {
        return fromDesignRequest(request, submitCapture, assetUpsertCapture, null, null);
    }

    /**
     * 从 Web 设计请求构建工具上下文，并注入会话 id 与 clientId 映射。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture,
                                                     Long aiChatSessionId,
                                                     java.util.Map<String, String> flowDesignClientIdMap) {
        AiDesignMentionSupport.ResolvedMentionContext resolved =
                AiDesignMentionSupport.resolve(request.getMentions());
        return build(
                request.getTestProjectId(),
                request.getTestFlowId(),
                request.isTemplateDesignMode() ? "template" : null,
                request.isTemplateDesignMode() ? request.getTemplateApis() : null,
                request.getGraphJson(),
                emptyToNull(resolved.getScopeApiIds()),
                emptyToNull(resolved.getContextNodeIds()),
                resolved.getContextRunId(),
                submitCapture,
                assetUpsertCapture,
                aiChatSessionId,
                flowDesignClientIdMap);
    }

    /**
     * 从 MCP 调用请求构建工具上下文。
     * 项目 id 优先用 params.testProjectId，否则用 Token 解析出的项目 id。
     *
     * @param tokenProjectId Project Token 过滤器写入的项目 id
     */
    public FlowDesignToolContext fromMcpRequest(McpToolInvokeParams params, Long tokenProjectId) {
        return fromMcpRequest(params, tokenProjectId, null);
    }

    /**
     * 从 MCP 调用请求构建工具上下文；可附带 submit 捕获器。素材提案捕获器固定为 null（MCP 无写入素材工具）。
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
                null,
                null,
                params.getGraphJson(),
                parseLongIds(params.getScopeApiIds()),
                emptyToNull(params.getContextNodeIds()),
                params.getContextRunId(),
                submitCapture,
                null,
                null,
                null);
    }

    /**
     * 组装工具上下文各字段，并填入运行时条数 / 字节上限配置。
     */
    private FlowDesignToolContext build(Long testProjectId,
                                        Long testFlowId,
                                        String designMode,
                                        com.alibaba.fastjson2.JSONArray templateApis,
                                        GraphJson graphJson,
                                        List<Long> scopeApiIds,
                                        List<String> contextNodeIds,
                                        Long contextRunId,
                                        FlowDesignSubmitCapture submitCapture,
                                        AssetUpsertCapture assetUpsertCapture,
                                        Long aiChatSessionId,
                                        java.util.Map<String, String> flowDesignClientIdMap) {
        java.util.Map<String, String> idMap = flowDesignClientIdMap != null
                ? flowDesignClientIdMap
                : new java.util.HashMap<>();
        return FlowDesignToolContext.builder()
                .testProjectId(testProjectId)
                .testFlowId(testFlowId)
                .designMode(designMode)
                .templateApis(templateApis)
                .graphJson(graphJson)
                .scopeApiIds(scopeApiIds)
                .contextNodeIds(contextNodeIds)
                .contextRunId(contextRunId)
                .maxSearchApis(aiLlmConfigService.getMaxSearchApis())
                .maxListFlows(aiLlmConfigService.getMaxListFlows())
                .maxToolResultBytes(aiLlmConfigService.getMaxToolResultBytes())
                .submitCapture(submitCapture)
                .assetUpsertCapture(assetUpsertCapture)
                .aiChatSessionId(aiChatSessionId)
                .flowDesignClientIdMap(idMap)
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
