package com.qualitest.plugin.hook;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.invoke.PluginInvokeRequest;
import com.qualitest.plugin.invoke.PluginInvokeResult;
import com.qualitest.plugin.invoke.PluginInvoker;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestPluginEnvBindingService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按已启用插件说明书 hooks 调度能力（onCheckpoint / onRestore / onRunPrepare 等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginHookRunner {

    private final ITestPluginEnvBindingService bindingService;
    private final PluginEnableService enableService;
    private final PluginInvoker pluginInvoker;

    /**
     * 是否存在已启用且声明了指定挂点的插件。
     */
    public boolean hasEnabledHook(Long envId, String hookName) {
        return !collectJobs(envId, hookName).isEmpty();
    }

    /**
     * 跑指定挂点；按 order 升序依次 invoke。
     * 每个 job 的 args 由调用方通过 argsFactory 按绑定 config 生成。
     *
     * @return 各次调用结果（capability 顺序）
     */
    public List<HookInvokeOutcome> run(
            TestProjectEnv env,
            Long runId,
            String hookName,
            ArgsFactory argsFactory
    ) {
        if (env == null || env.getTestProjectEnvId() == null || hookName == null) {
            return List.of();
        }
        List<HookJob> jobs = collectJobs(env.getTestProjectEnvId(), hookName);
        jobs.sort(Comparator.comparingInt(HookJob::getOrder));
        List<HookInvokeOutcome> outcomes = new ArrayList<>();
        for (HookJob job : jobs) {
            Map<String, Object> args = argsFactory == null
                    ? Map.of()
                    : argsFactory.build(job.getPluginId(), job.getCapability(), job.getConfig());
            log.info("plugin hook={} pluginId={} capability={} runId={}",
                    hookName, job.getPluginId(), job.getCapability(), runId);
            PluginInvokeResult result = pluginInvoker.invoke(PluginInvokeRequest.builder()
                    .projectId(env.getTestProjectId())
                    .envId(env.getTestProjectEnvId())
                    .runId(runId)
                    .pluginId(job.getPluginId())
                    .capability(job.getCapability())
                    .args(args == null ? Map.of() : args)
                    .source(hookName)
                    .build());
            outcomes.add(HookInvokeOutcome.builder()
                    .pluginId(job.getPluginId())
                    .capability(job.getCapability())
                    .result(result)
                    .build());
        }
        return outcomes;
    }

    /**
     * onRunPrepare：从绑定 config 取 scenarioId / autoReset 等通用键写入 args。
     *
     * @return 各次 Invoke 结果，供跑流写入审计步
     */
    public List<HookInvokeOutcome> runPrepare(TestProjectEnv env, Long runId) {
        return run(env, runId, "onRunPrepare", (pluginId, capability, config) -> {
            Map<String, Object> args = new LinkedHashMap<>();
            String scenarioId = config.getString("scenarioId");
            if (scenarioId != null && !scenarioId.isBlank()) {
                args.put("scenarioId", scenarioId.trim());
            } else if ("seed.apply".equals(capability)) {
                throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID,
                        "已启用 " + pluginId + " 但未配置 scenarioId");
            }
            Boolean autoReset = config.getBoolean("autoReset");
            if (Boolean.FALSE.equals(autoReset)) {
                args.put("noReset", true);
            }
            return args;
        });
    }

    private List<HookJob> collectJobs(Long envId, String hookName) {
        List<TestPluginEnvBinding> bindings = bindingService.selectTestPluginEnvBindingList(
                TestPluginEnvBinding.builder()
                        .testProjectEnvId(envId)
                        .enabled(1)
                        .build());
        List<HookJob> jobs = new ArrayList<>();
        if (bindings == null) {
            return jobs;
        }
        for (TestPluginEnvBinding binding : bindings) {
            if (binding.getDelStatus() != null && binding.getDelStatus() != 0) {
                continue;
            }
            if (binding.getEnabled() == null || binding.getEnabled() != 1) {
                continue;
            }
            PluginManifest manifest;
            try {
                manifest = enableService.requireManifest(binding.getPluginId());
            } catch (Exception e) {
                log.warn("hook collect skip bad manifest pluginId={}", binding.getPluginId());
                continue;
            }
            JSONObject config = parseConfig(binding.getConfigJson());
            for (PluginManifest.Hook hook : manifest.getHooks()) {
                if (!hookName.equals(hook.getName())) {
                    continue;
                }
                for (String action : hook.getActions() == null ? List.<String>of() : hook.getActions()) {
                    jobs.add(HookJob.builder()
                            .pluginId(binding.getPluginId())
                            .order(hook.getOrder())
                            .capability(action)
                            .config(config)
                            .build());
                }
            }
        }
        return jobs;
    }

    private static JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject o = JSON.parseObject(configJson);
            return o == null ? new JSONObject() : o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @FunctionalInterface
    public interface ArgsFactory {
        Map<String, Object> build(String pluginId, String capability, JSONObject config);
    }

    @Getter
    @Builder
    public static class HookJob {
        private final String pluginId;
        private final int order;
        private final String capability;
        private final JSONObject config;
    }

    @Getter
    @Builder
    public static class HookInvokeOutcome {
        private final String pluginId;
        private final String capability;
        private final PluginInvokeResult result;
    }
}
