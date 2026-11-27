package com.qualitest.flow.inspect;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.flow.validate.FlowIssueCodes;
import com.qualitest.plugin.hook.PluginHookRunner;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开跑阶段调度已启用插件的流检查挂点，并把回包转成统一问题列表。
 * 整次调度失败或单插件 Invoke 失败时产出 WARNING，不直接中断其它检查收集。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginInvokeInspectionCollector {

    /** 流检查挂点名 */
    public static final String HOOK_ON_FLOW_INSPECT = "onFlowInspect";

    private final PluginHookRunner hookRunner;
    private final ITestProjectEnvService envService;

    /**
     * 对指定环境执行插件流检查。
     *
     * @param envId          跑流环境 id；为空则跳过
     * @param testProjectId  项目 id，写入挂点入参
     * @param testFlowId     当前流 id，写入挂点入参
     * @param graphJson      当前图 JSON
     * @param stage          问题阶段；为空时按开跑阶段
     */
    public List<FlowIssue> collect(
            Long envId,
            Long testProjectId,
            Long testFlowId,
            String graphJson,
            FlowIssue.Stage stage) {
        if (envId == null) {
            return List.of();
        }
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.RUN;
        TestProjectEnv env = envService.selectTestProjectEnvById(envId);
        if (env == null) {
            return List.of();
        }
        List<FlowIssue> issues = new ArrayList<>();
        List<PluginHookRunner.HookInvokeOutcome> outcomes;
        try {
            outcomes = hookRunner.run(
                    env,
                    null,
                    HOOK_ON_FLOW_INSPECT,
                    (pluginId, capability, config) -> {
                        Map<String, Object> args = new LinkedHashMap<>();
                        args.put("graphJson", graphJson);
                        args.put("testProjectId", testProjectId);
                        args.put("testFlowId", testFlowId);
                        args.put("testProjectEnvId", envId);
                        return args;
                    });
        } catch (RuntimeException e) {
            log.warn("onFlowInspect 调度失败 envId={}", envId, e);
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            issues.add(FlowIssue.warning(
                    FlowIssueCodes.INSPECT_INVOKE_FAILED,
                    effective,
                    null,
                    "流检查插件调度失败：" + msg));
            return issues;
        }
        if (outcomes == null || outcomes.isEmpty()) {
            return issues;
        }
        for (PluginHookRunner.HookInvokeOutcome outcome : outcomes) {
            if (outcome == null) {
                continue;
            }
            issues.addAll(parseOutcome(outcome, effective));
        }
        return issues;
    }

    /**
     * 解析单个插件挂点回包。
     * 失败或 JSON 无法解析时记一条带插件前缀的 WARNING；
     * 成功时读取 {@code issues[]}，每条至少要有短码，缺文案时用短码顶替。
     */
    private List<FlowIssue> parseOutcome(
            PluginHookRunner.HookInvokeOutcome outcome, FlowIssue.Stage stage) {
        List<FlowIssue> issues = new ArrayList<>();
        String pluginId = outcome.getPluginId();
        if (pluginId == null || pluginId.isBlank()) {
            issues.add(FlowIssue.warning(
                    FlowIssueCodes.INSPECT_INVOKE_FAILED,
                    stage,
                    null,
                    "插件流检查回包缺少 pluginId"));
            return issues;
        }
        PluginInvokeResult result = outcome.getResult();
        if (result == null || !result.isOk()) {
            String msg = result != null && result.getStderr() != null && !result.getStderr().isBlank()
                    ? result.getStderr().trim()
                    : "插件检查 Invoke 失败";
            issues.add(FlowIssue.warning(
                    PluginInspectionManifestSupport.qualifiedCode(
                            pluginId, FlowIssueCodes.INSPECT_INVOKE_FAILED),
                    stage,
                    null,
                    "插件「" + pluginId + "」流检查失败：" + msg));
            return issues;
        }
        String parsed = result.getParsedJson();
        if (parsed == null || parsed.isBlank()) {
            parsed = result.getStdout();
        }
        JSONObject root;
        try {
            root = parsed != null && !parsed.isBlank() ? JSON.parseObject(parsed) : null;
        } catch (JSONException e) {
            issues.add(FlowIssue.warning(
                    PluginInspectionManifestSupport.qualifiedCode(
                            pluginId, FlowIssueCodes.INSPECT_INVOKE_FAILED),
                    stage,
                    null,
                    "插件「" + pluginId + "」流检查回包无法解析"));
            return issues;
        }
        if (root == null) {
            return issues;
        }
        JSONArray arr = root.getJSONArray("issues");
        if (arr == null || arr.isEmpty()) {
            return issues;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String shortCode = o.getString("code");
            if (shortCode == null || shortCode.isBlank()) {
                continue;
            }
            String message = o.getString("message");
            if (message == null || message.isBlank()) {
                message = shortCode;
            }
            issues.add(FlowIssue.error(
                    PluginInspectionManifestSupport.qualifiedCode(pluginId, shortCode.trim()),
                    stage,
                    o.getString("nodeId"),
                    message));
        }
        return issues;
    }
}
