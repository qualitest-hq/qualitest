package com.qualitest.flow.node.impl;

import cn.hutool.core.util.StrUtil;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandler;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.invoke.PluginInvokeRequest;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.plugin.invoke.PluginInvoker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台插件步骤执行器：按节点 data 中的 pluginId / capability / config 调用插件能力。
 * <p>
 * 插件是否可用由当前环境的启用集合即时决定；插件未启用或缺少对应能力时步骤直接失败，不会静默跳过。
 * 配置缺失、调用失败均返回 FAILED 步骤，不向外抛异常，以便跑图走失败后继续与收束。
 */
@Component
public class PluginActionNodeHandler implements NodeHandler {

    /** 插件调用器 */
    private final PluginInvoker pluginInvoker;

    /** 构造插件步骤执行器 */
    public PluginActionNodeHandler(PluginInvoker pluginInvoker) {
        this.pluginInvoker = pluginInvoker;
    }

    /** 判断是否为插件步骤节点类型 */
    @Override
    public boolean supports(String type) {
        return FlowNodeType.PLUGIN_ACTION.matches(type);
    }

    /**
     * 执行插件步骤：校验必填配置与环境绑定，调用插件能力，成功时记录标准输出并返回通过的步骤结果。
     * 配置缺失、插件能力不可用、插件返回失败或调用异常都会转为 FAILED 步骤。
     */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        String name = AbstractStubNodeHandler.resolveNodeName(node);
        Map<String, Object> data = node != null && node.getData() != null ? node.getData() : Map.of();
        String pluginId = stringVal(data.get("pluginId"));
        String capability = stringVal(data.get("capability"));
        String contributionId = stringVal(data.get("contributionId"));
        if (pluginId.isBlank() || capability.isBlank()) {
            return failed(node, incomingEdgeId, name, t0, ctx,
                    FlowErrorCode.TF_NODE_CAPABILITY_UNAVAILABLE,
                    "pluginAction 须配置 pluginId 与 capability");
        }
        if (ctx == null || ctx.getTestProjectEnvId() == null) {
            return failed(node, incomingEdgeId, name, t0, ctx,
                    FlowErrorCode.TF_NODE_CAPABILITY_UNAVAILABLE,
                    "pluginAction 需要已绑定测试环境");
        }

        // 把节点 config 展开为插件调用参数
        Map<String, Object> args = new HashMap<>();
        Object config = data.get("config");
        if (config instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    args.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }

        try {
            PluginInvokeResult result = pluginInvoker.invoke(PluginInvokeRequest.builder()
                    .projectId(ctx.getTestProjectId())
                    .envId(ctx.getTestProjectEnvId())
                    .runId(ctx.getTestFlowRunId())
                    .pluginId(pluginId)
                    .capability(capability)
                    .args(args)
                    .source("pluginAction:" + StrUtil.blankToDefault(contributionId, capability))
                    .build());
            if (result == null || !result.isOk()) {
                String detail = result != null && StrUtil.isNotBlank(result.getStderr())
                        ? result.getStderr()
                        : "插件步骤退出码非 0";
                return failed(node, incomingEdgeId, name, t0, ctx,
                        FlowErrorCode.TF_PLUGIN_ACTION_FAILED, detail);
            }
            if (result.getStdout() != null && !result.getStdout().isBlank()) {
                ctx.getFlow().put("pluginAction.lastStdout", result.getStdout());
            }
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(node.getType())
                    .nodeName(name)
                    .edgeId(incomingEdgeId)
                    .status(RunStatus.PASSED.getCode())
                    .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                    .flowAfter(AbstractStubNodeHandler.copyFlow(ctx))
                    .build();
        } catch (PluginException e) {
            return failed(node, incomingEdgeId, name, t0, ctx,
                    FlowErrorCode.TF_NODE_CAPABILITY_UNAVAILABLE,
                    e.getMessage() != null ? e.getMessage() : "插件能力不可用");
        } catch (FlowExecutionException e) {
            return failed(node, incomingEdgeId, name, t0, ctx, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            return failed(node, incomingEdgeId, name, t0, ctx,
                    FlowErrorCode.TF_PLUGIN_ACTION_FAILED,
                    e.getMessage() != null ? e.getMessage() : "插件步骤执行失败");
        }
    }

    /** 构造 FAILED 步骤 */
    private static StepResult failed(
            GraphNode node,
            String incomingEdgeId,
            String name,
            long t0,
            FlowRunContext ctx,
            FlowErrorCode code,
            String message) {
        return StepResult.builder()
                .nodeId(node != null ? node.getId() : null)
                .nodeType(node != null ? node.getType() : FlowNodeType.PLUGIN_ACTION.getCode())
                .nodeName(name)
                .edgeId(incomingEdgeId)
                .status(RunStatus.FAILED.getCode())
                .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                .error(StepError.of(code, message))
                .flowAfter(AbstractStubNodeHandler.copyFlow(ctx))
                .build();
    }

    /** 转为去首尾空白的字符串，null 返回空串 */
    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
