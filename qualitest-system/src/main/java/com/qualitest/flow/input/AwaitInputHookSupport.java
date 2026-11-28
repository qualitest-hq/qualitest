package com.qualitest.flow.input;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.plugin.hook.PluginHookRunner;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Input 节点可选自动填表：调度宿主挂点 {@code onAwaitInput}。
 * <p>
 * 轮到 Input 时先问本环境是否已启用该挂点；未启用则返回 empty，调用方再按 pausePermitted 决定暂停或失败。
 * 已启用时向插件传入 nodeId / fields / prompt（及插件 config），解析成功结果 JSON 中的 {@code inputs} 对象。
 * 插件只负责给出字段值，不得改 Run 状态；校验写入 flow、置 passed / paused / failed 由 Input 节点处理器完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwaitInputHookSupport {

    /** 宿主挂点名：轮到 Input 时先问插件能否填表 */
    public static final String HOOK_ON_AWAIT_INPUT = "onAwaitInput";

    private final PluginHookRunner pluginHookRunner;
    private final ITestProjectEnvService testProjectEnvService;

    /**
     * 尝试让已启用插件自动填 Input 字段。
     *
     * @param ctx  当前 Run 上下文（须带 testProjectEnvId）
     * @param node Input 节点
     * @return 填好的字段 name→值；无法自动填时 empty
     */
    public Optional<Map<String, Object>> tryAutoFill(FlowRunContext ctx, GraphNode node) {
        if (ctx == null || ctx.getTestProjectEnvId() == null) {
            return Optional.empty();
        }
        if (!pluginHookRunner.hasEnabledHook(ctx.getTestProjectEnvId(), HOOK_ON_AWAIT_INPUT)) {
            return Optional.empty();
        }
        TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(ctx.getTestProjectEnvId());
        if (env == null) {
            return Optional.empty();
        }
        Map<String, Object> data = node != null && node.getData() != null ? node.getData() : Map.of();
        List<PluginHookRunner.HookInvokeOutcome> outcomes;
        try {
            outcomes = pluginHookRunner.run(
                    env,
                    ctx.getTestFlowRunId(),
                    HOOK_ON_AWAIT_INPUT,
                    (pluginId, capability, config) -> {
                        Map<String, Object> args = new LinkedHashMap<>();
                        args.put("nodeId", node != null ? node.getId() : null);
                        args.put("fields", data.get("fields"));
                        args.put("prompt", data.get("prompt"));
                        if (config != null && !config.isEmpty()) {
                            args.put("config", config);
                        }
                        return args;
                    });
        } catch (Exception e) {
            log.warn("onAwaitInput 调度失败 envId={}", ctx.getTestProjectEnvId(), e);
            return Optional.empty();
        }
        if (outcomes == null || outcomes.isEmpty()) {
            return Optional.empty();
        }
        for (PluginHookRunner.HookInvokeOutcome outcome : outcomes) {
            Optional<Map<String, Object>> inputs = extractInputs(outcome);
            if (inputs.isPresent()) {
                return inputs;
            }
        }
        return Optional.empty();
    }

    /**
     * 从单次挂点调用结果中取出 {@code inputs} 对象。
     * 优先读 parsedJson，否则读 stdout；JSON 无 inputs 或失败则 empty。
     */
    private static Optional<Map<String, Object>> extractInputs(PluginHookRunner.HookInvokeOutcome outcome) {
        if (outcome == null || outcome.getResult() == null) {
            return Optional.empty();
        }
        PluginInvokeResult result = outcome.getResult();
        if (!result.isOk()) {
            return Optional.empty();
        }
        String jsonText = result.getParsedJson();
        if (jsonText == null || jsonText.isBlank()) {
            jsonText = result.getStdout();
        }
        if (jsonText == null || jsonText.isBlank()) {
            return Optional.empty();
        }
        try {
            JSONObject jo = JSON.parseObject(jsonText);
            if (jo == null) {
                return Optional.empty();
            }
            JSONObject inputs = jo.getJSONObject("inputs");
            if (inputs == null || inputs.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new LinkedHashMap<>(inputs));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
