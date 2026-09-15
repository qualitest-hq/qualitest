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
 * 组装造流工具请求上下文的工厂。
 * <p>
 * Web：注入 SubmitCapture、素材/鉴权提案容器、会话短名映射与基准画布。
 * MCP：只读信封（项目/流/画布），不注入素材或鉴权写入容器。
 */
@Component
@RequiredArgsConstructor
public class FlowDesignToolContextFactory {

    private final AiLlmConfigService aiLlmConfigService;

    /**
     * 从 Web 设计请求构建上下文（不注入素材提案容器）。
     * 解析 mentions 得到检索 API 范围与上下文节点 / Run；submitCapture 由调用方传入。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture) {
        return fromDesignRequest(request, submitCapture, null);
    }

    /**
     * 从 Web 设计请求构建上下文，并注入画布 SubmitCapture 与素材提案容器。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture) {
        return fromDesignRequest(request, submitCapture, assetUpsertCapture, null, null);
    }

    /**
     * 从 Web 设计请求构建完整上下文：会话 id、短名映射；全自动默认关、无落盘回调。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture,
                                                     Long aiChatSessionId,
                                                     java.util.Map<String, String> flowDesignClientIdMap) {
        return fromDesignRequest(request, submitCapture, assetUpsertCapture,
                aiChatSessionId, flowDesignClientIdMap, false, null);
    }

    /**
     * 从 Web 设计请求构建完整上下文。
     *
     * @param autopilotEnabled  是否全自动（允许 run_test_flow、隐式落盘、素材与鉴权直写）
     * @param onGraphCommitted  隐式落盘成功回调；SSE 层用于推送 graphCommitted
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture,
                                                     Long aiChatSessionId,
                                                     java.util.Map<String, String> flowDesignClientIdMap,
                                                     boolean autopilotEnabled,
                                                     java.util.function.BiConsumer<Long, GraphJson> onGraphCommitted) {
        return fromDesignRequest(request, submitCapture, assetUpsertCapture, null,
                aiChatSessionId, flowDesignClientIdMap, autopilotEnabled, onGraphCommitted, null);
    }

    /**
     * 从 Web 设计请求构建完整上下文（含素材与鉴权提案容器、会话 id、短名映射、
     * 全自动开关、落盘成功回调、Run 已触发回调，以及默认场景/环境）。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture,
                                                     AuthProfileUpsertCapture authProfileUpsertCapture,
                                                     Long aiChatSessionId,
                                                     java.util.Map<String, String> flowDesignClientIdMap,
                                                     boolean autopilotEnabled,
                                                     java.util.function.BiConsumer<Long, GraphJson> onGraphCommitted,
                                                     java.util.function.Consumer<Long> onRunStarted) {
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
                authProfileUpsertCapture,
                aiChatSessionId,
                flowDesignClientIdMap,
                autopilotEnabled,
                onGraphCommitted,
                onRunStarted,
                blankToNull(request.getRunScenarioId()),
                request.getTestProjectEnvId());
    }

    /**
     * 不需要「Run 已触发」回调时使用：onRunStarted 置为 null。
     */
    public FlowDesignToolContext fromDesignRequest(TestFlowDesignRequest request,
                                                     FlowDesignSubmitCapture submitCapture,
                                                     AssetUpsertCapture assetUpsertCapture,
                                                     AuthProfileUpsertCapture authProfileUpsertCapture,
                                                     Long aiChatSessionId,
                                                     java.util.Map<String, String> flowDesignClientIdMap,
                                                     boolean autopilotEnabled,
                                                     java.util.function.BiConsumer<Long, GraphJson> onGraphCommitted) {
        return fromDesignRequest(request, submitCapture, assetUpsertCapture, authProfileUpsertCapture,
                aiChatSessionId, flowDesignClientIdMap, autopilotEnabled, onGraphCommitted, null);
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
     * 从 MCP 调用请求构建工具上下文；可附带 submit 捕获器。素材与鉴权提案捕获器固定为 null。
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
                null,
                null,
                false,
                null,
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
                                        AuthProfileUpsertCapture authProfileUpsertCapture,
                                        Long aiChatSessionId,
                                        java.util.Map<String, String> flowDesignClientIdMap,
                                        boolean autopilotEnabled,
                                        java.util.function.BiConsumer<Long, GraphJson> onGraphCommitted,
                                        java.util.function.Consumer<Long> onRunStarted,
                                        String defaultRunScenarioId,
                                        Long defaultTestProjectEnvId) {
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
                .autopilotEnabled(autopilotEnabled)
                .onGraphCommitted(onGraphCommitted)
                .onRunStarted(onRunStarted)
                .defaultRunScenarioId(defaultRunScenarioId)
                .defaultTestProjectEnvId(defaultTestProjectEnvId)
                .maxSearchApis(aiLlmConfigService.getMaxSearchApis())
                .maxListFlows(aiLlmConfigService.getMaxListFlows())
                .maxToolResultBytes(aiLlmConfigService.getMaxToolResultBytes())
                .submitCapture(submitCapture)
                .assetUpsertCapture(assetUpsertCapture)
                .authProfileUpsertCapture(authProfileUpsertCapture)
                .aiChatSessionId(aiChatSessionId)
                .flowDesignClientIdMap(idMap)
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
