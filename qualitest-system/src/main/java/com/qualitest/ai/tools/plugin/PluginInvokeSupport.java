package com.qualitest.ai.tools.plugin;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.invoke.PluginInvokeRequest;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.plugin.invoke.PluginInvoker;
import com.qualitest.plugin.manifest.PluginAiTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行插件说明书贡献的 AI 工具：按工具名解析启用贡献 → {@link PluginInvoker}。
 * <p>
 * 不写 Staging / 不改 graphJson。
 */
@Component
@RequiredArgsConstructor
public class PluginInvokeSupport {

    private final PluginAiToolCatalog catalog;
    private final PluginInvoker pluginInvoker;

    /**
     * 当前项目是否贡献了该工具名。
     */
    public boolean isContributedTool(Long testProjectId, String toolName) {
        return catalog.findContributedTool(testProjectId, toolName) != null;
    }

    /**
     * 若为已贡献工具则执行并返回 JSON；未贡献返回 {@code null}（由 Executor 统一报「未知工具」）。
     * 只点查目录一次。
     */
    public String invokeIfContributed(String toolName, Map<String, Object> args, FlowDesignToolContext ctx) {
        Long projectId = ctx != null ? ctx.getTestProjectId() : null;
        PluginAiToolCatalog.ContributedTool contributed =
                catalog.findContributedTool(projectId, toolName);
        if (contributed == null) {
            return null;
        }
        return invokeContributed(toolName, args, ctx, contributed);
    }

    /**
     * 按贡献的工具名执行；未贡献时返回 error JSON。
     *
     * @param toolName 说明书 ai.tools[].name
     * @param args     模型参数（能力参数 + 可选 envId）
     * @param ctx      造流 / MCP 上下文
     */
    public String invoke(String toolName, Map<String, Object> args, FlowDesignToolContext ctx) {
        String result = invokeIfContributed(toolName, args, ctx);
        if (result == null) {
            return FlowDesignToolSupport.errorJson("未知或未启用的插件工具: " + toolName);
        }
        return result;
    }

    private String invokeContributed(String toolName, Map<String, Object> args, FlowDesignToolContext ctx,
                                     PluginAiToolCatalog.ContributedTool contributed) {
        PluginAiTool tool = contributed.tool();
        Long envId = resolveEnvId(args, ctx);
        if (envId == null) {
            return FlowDesignToolSupport.errorJson(
                    "缺少测试环境 id：请传 envId，或在造流请求中带默认环境");
        }

        Long projectId = ctx != null ? ctx.getTestProjectId() : null;
        Map<String, Object> invokeArgs = capabilityArgs(args);
        String source = resolveSource(ctx);
        try {
            PluginInvokeResult result = pluginInvoker.invoke(PluginInvokeRequest.builder()
                    .projectId(projectId)
                    .envId(envId)
                    .pluginId(contributed.pluginId())
                    .capability(tool.getCapability())
                    .args(invokeArgs)
                    .source(source)
                    .build());
            return successJson(contributed.pluginId(), tool.getCapability(), toolName, envId, result);
        } catch (PluginException e) {
            String code = e.getErrorCode() != null ? e.getErrorCode().name() : "PLUGIN_ERROR";
            return FlowDesignToolSupport.errorJson(code + ": " + e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("invoke 失败: " + e.getMessage());
        }
    }

    private static String resolveSource(FlowDesignToolContext ctx) {
        String endpoint = ctx != null ? ctx.getEditLeaseEndpoint() : null;
        if (endpoint != null && endpoint.startsWith("mcp")) {
            return "mcp-invoke";
        }
        return "ai-invoke";
    }

    private static Long resolveEnvId(Map<String, Object> args, FlowDesignToolContext ctx) {
        Long fromArgs = FlowDesignToolSupport.longArg(args != null ? args.get("envId") : null);
        if (fromArgs == null && args != null) {
            fromArgs = FlowDesignToolSupport.longArg(args.get("testProjectEnvId"));
        }
        if (fromArgs != null) {
            return fromArgs;
        }
        return ctx != null ? ctx.getDefaultTestProjectEnvId() : null;
    }

    /** 去掉平台保留键后作为 capability args */
    private static Map<String, Object> capabilityArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            if ("envId".equals(k) || "testProjectEnvId".equals(k)) {
                continue;
            }
            out.put(k, e.getValue());
        }
        return out;
    }

    private static String successJson(String pluginId, String capability, String toolName, Long envId,
                                      PluginInvokeResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", result != null && result.isOk());
        body.put("toolName", toolName);
        body.put("pluginId", pluginId);
        body.put("capability", capability);
        body.put("envId", envId);
        if (result != null) {
            body.put("exitCode", result.getExitCode());
            if (result.getStdout() != null && !result.getStdout().isBlank()) {
                body.put("stdout", trim(result.getStdout(), 4000));
            }
            if (result.getStderr() != null && !result.getStderr().isBlank()) {
                body.put("stderr", trim(result.getStderr(), 2000));
            }
            if (result.getParsedJson() != null && !result.getParsedJson().isBlank()) {
                body.put("parsedJson", trim(result.getParsedJson(), 4000));
            }
        }
        return JSON.toJSONString(body);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }
}
