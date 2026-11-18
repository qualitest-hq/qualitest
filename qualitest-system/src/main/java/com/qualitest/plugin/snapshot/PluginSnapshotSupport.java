package com.qualitest.plugin.snapshot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.hook.PluginHookRunner;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跑流快照挂点：经 PluginHookRunner 调度已启用插件的 onCheckpoint / onRestore。
 */
@Component
@RequiredArgsConstructor
public class PluginSnapshotSupport {

    private final PluginHookRunner hookRunner;

    /**
     * 当前环境是否有已启用的 onCheckpoint 插件。
     */
    public boolean isCheckpointProviderEnabled(Long envId) {
        return hookRunner.hasEnabledHook(envId, "onCheckpoint");
    }

    /**
     * 当前环境是否有已启用的 onRestore 插件。
     */
    public boolean isRestoreProviderEnabled(Long envId) {
        return hookRunner.hasEnabledHook(envId, "onRestore");
    }

    /**
     * 打存档：调度 onCheckpoint；saveId 由 runId、nodeId 与单调序号拼成。
     *
     * @return CLI 返回或回退使用的 saveId
     */
    public String createSave(TestProjectEnv env, Long runId, String nodeId, String label, int seq) {
        String saveId = sanitizeSaveId(runId + "-" + nodeId + "-" + Math.max(1, seq));
        List<PluginHookRunner.HookInvokeOutcome> outcomes = hookRunner.run(
                env, runId, "onCheckpoint",
                (pluginId, capability, config) -> {
                    Map<String, Object> args = new LinkedHashMap<>();
                    args.put("saveId", saveId);
                    return args;
                });
        if (outcomes.isEmpty()) {
            throw new PluginException(PluginErrorCode.PROVIDER_NOT_FOUND, "未启用声明 onCheckpoint 的插件");
        }
        PluginInvokeResult result = outcomes.get(0).getResult();
        String id = extractSaveId(result == null ? null : result.getStdout(), saveId);
        if (id == null || id.isBlank()) {
            throw new PluginException(PluginErrorCode.INVOKE_FAILED, "onCheckpoint 未返回 saveId");
        }
        return id;
    }

    /**
     * 按 saveId 读档：调度 onRestore。
     */
    public void restoreSave(TestProjectEnv env, Long runId, String saveId, String nodeId) {
        List<PluginHookRunner.HookInvokeOutcome> outcomes = hookRunner.run(
                env, runId, "onRestore",
                (pluginId, capability, config) -> {
                    Map<String, Object> args = new LinkedHashMap<>();
                    args.put("saveId", saveId);
                    return args;
                });
        if (outcomes.isEmpty()) {
            throw new PluginException(PluginErrorCode.PROVIDER_NOT_FOUND, "未启用声明 onRestore 的插件");
        }
    }

    /** 最近一次 checkpoint 使用的 pluginId（供审计文案）。 */
    public String describeCheckpointProvider(Long envId) {
        return hookRunner.hasEnabledHook(envId, "onCheckpoint") ? "plugin:onCheckpoint" : "none";
    }

    private String extractSaveId(String stdout, String fallback) {
        if (stdout == null || stdout.isBlank()) {
            return fallback;
        }
        try {
            JSONObject json = JSON.parseObject(stdout.trim());
            if (json != null && json.getString("saveId") != null) {
                return json.getString("saveId");
            }
        } catch (Exception ignored) {
            // ignore
        }
        return fallback;
    }

    private String sanitizeSaveId(String raw) {
        String s = raw.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (s.isBlank()) {
            s = "save";
        }
        if (s.length() > 128) {
            s = s.substring(0, 128);
        }
        if (!Character.isLetterOrDigit(s.charAt(0))) {
            s = "s" + s;
        }
        return s;
    }
}
