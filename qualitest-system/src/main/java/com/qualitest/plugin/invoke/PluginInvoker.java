package com.qualitest.plugin.invoke;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginDestructiveGate;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.channel.EnvExecChannelSupport;
import com.qualitest.plugin.channel.ExecAgentChannel;
import com.qualitest.plugin.channel.ExecChannelKind;
import com.qualitest.plugin.channel.LocalExecChannel;
import com.qualitest.plugin.config.PluginProperties;
import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.mount.ProjectMountSupport;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.TestPluginPackageParams;
import com.qualitest.project.service.ITestPluginPackageService;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台插件唯一运行入口。
 * 按说明书 runtime / projectMount / cli.argMap 执行；通道为 local-exec 或 exec-agent。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginInvoker {

    private final PluginEnableService enableService;
    private final ITestProjectEnvService envService;
    private final ITestPluginPackageService packageService;
    private final LocalExecChannel localExecChannel;
    private final ExecAgentChannel execAgentChannel;
    private final EnvExecChannelSupport channelSupport;
    private final PluginProperties pluginProperties;

    /** 同环境写能力互斥：key=envId */
    private final ConcurrentHashMap<Long, Object> envWriteLocks = new ConcurrentHashMap<>();

    /**
     * 调用已启用插件的某项能力。
     */
    public PluginInvokeResult invoke(PluginInvokeRequest request) {
        if (request == null || request.getEnvId() == null || request.getPluginId() == null
                || request.getCapability() == null) {
            throw new PluginException(PluginErrorCode.INVOKE_FAILED, "invoke 参数不完整");
        }
        TestProjectEnv env = envService.selectTestProjectEnvById(request.getEnvId());
        if (env == null) {
            throw new PluginException(PluginErrorCode.PROVIDER_NOT_FOUND, "环境不存在");
        }
        TestPluginEnvBinding binding = enableService.requireEnabledBinding(request.getEnvId(), request.getPluginId());
        PluginManifest manifest = enableService.requireManifest(request.getPluginId());
        PluginManifest.Capability capability = manifest.findCapability(request.getCapability());
        if (capability == null) {
            throw new PluginException(PluginErrorCode.UNKNOWN_CAPABILITY,
                    "能力不属于插件 " + request.getPluginId() + ": " + request.getCapability());
        }
        if ("destructive".equals(capability.resolvedGate()) && !PluginDestructiveGate.isAllowed(env)) {
            throw new PluginException(PluginErrorCode.GATE_DENIED, "破坏性总闸未开");
        }

        String kind = manifest.getRuntime() == null ? null : manifest.getRuntime().getKind();
        if (!"ops-agent".equals(kind)) {
            throw new PluginException(PluginErrorCode.INVOKE_FAILED, "暂仅支持 ops-agent: " + kind);
        }
        boolean writeEffect = "write".equalsIgnoreCase(capability.getEffect());
        if (writeEffect) {
            Object lock = envWriteLocks.computeIfAbsent(request.getEnvId(), id -> new Object());
            synchronized (lock) {
                return doInvoke(request, env, binding, manifest, capability);
            }
        }
        return doInvoke(request, env, binding, manifest, capability);
    }

    private PluginInvokeResult doInvoke(
            PluginInvokeRequest request,
            TestProjectEnv env,
            TestPluginEnvBinding binding,
            PluginManifest manifest,
            PluginManifest.Capability capability
    ) {
        PluginManifest.CliSpec cli = manifest.getRuntime().getAgent().getCli();
        String configText = buildConfig(request, binding, env, manifest);
        EnvExecChannelSupport.ChannelEndpoint channel = channelSupport.resolve(request.getEnvId());

        List<String> pingArgs = new ArrayList<>(cli.getPingArgs() == null ? List.of("ping") : cli.getPingArgs());
        LocalExecChannel.ExecResult ping = execViaChannel(channel, cli.getBin(), pingArgs, configText,
                Math.min(30_000L, pluginProperties.getExecTimeoutMs()));
        if (ping.getExitCode() != 0) {
            throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE,
                    "CLI ping 失败: " + trim(ping.getStdout(), ping.getStderr()));
        }
        assertCliVersion(ping.getStdout(), request.getPluginId());

        List<String> args = buildCapabilityArgs(request, cli);
        LocalExecChannel.ExecResult result = execViaChannel(channel, cli.getBin(), args, configText,
                pluginProperties.getExecTimeoutMs());
        log.info("plugin invoke pluginId={} capability={} channel={} source={} exit={} runId={}",
                request.getPluginId(), request.getCapability(), channel.getKind().getWire(),
                request.getSource(), result.getExitCode(), request.getRunId());
        if (result.getExitCode() != 0) {
            throw new PluginException(PluginErrorCode.INVOKE_FAILED,
                    "CLI 失败: " + trim(result.getStdout(), result.getStderr()));
        }
        return PluginInvokeResult.builder()
                .ok(true)
                .exitCode(result.getExitCode())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .parsedJson(result.getStdout())
                .build();
    }

    private LocalExecChannel.ExecResult execViaChannel(
            EnvExecChannelSupport.ChannelEndpoint channel,
            String bin,
            List<String> args,
            String configText,
            long timeoutMs
    ) {
        if (channel.getKind() == ExecChannelKind.EXEC_AGENT) {
            return execAgentChannel.exec(channel.getBaseUrl(), channel.getToken(), bin, args, configText, timeoutMs);
        }
        return localExecChannel.exec(bin, args, configText, timeoutMs);
    }

    private void assertCliVersion(String pingStdout, String pluginId) {
        TestPluginPackage pkg = packageService.selectTestPluginPackageOne(
                TestPluginPackageParams.builder().pluginId(pluginId).build());
        if (pkg == null || pingStdout == null || pingStdout.isBlank()) {
            return;
        }
        try {
            JSONObject json = JSON.parseObject(pingStdout.trim());
            if (json == null) {
                return;
            }
            String cliVersion = json.getString("version");
            if (cliVersion != null && !cliVersion.isBlank() && !cliVersion.equals(pkg.getVersion())) {
                throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE,
                        "被测端 CLI 版本 " + cliVersion + " 不同于登记版本 " + pkg.getVersion() + "，请升级被测端");
            }
        } catch (PluginException e) {
            throw e;
        } catch (Exception ignored) {
            // ping 非 JSON 时跳过版本校验
        }
    }

    private String buildConfig(
            PluginInvokeRequest request,
            TestPluginEnvBinding binding,
            TestProjectEnv env,
            PluginManifest manifest
    ) {
        JSONObject config = new JSONObject();
        if (binding.getConfigJson() != null && !binding.getConfigJson().isBlank()) {
            JSONObject parsed = JSON.parseObject(binding.getConfigJson());
            if (parsed != null) {
                config.putAll(parsed);
            }
        }
        mergeCurrentProjectMount(config, env.getTestProjectId(), manifest);
        config.put("pluginId", request.getPluginId());
        config.put("projectId", String.valueOf(request.getProjectId() != null ? request.getProjectId() : env.getTestProjectId()));
        config.put("envId", String.valueOf(request.getEnvId()));
        if (request.getRunId() != null) {
            config.put("runId", String.valueOf(request.getRunId()));
        }
        config.put("allowDestructive", PluginDestructiveGate.isAllowed(env));
        return config.toJSONString();
    }

    private void mergeCurrentProjectMount(JSONObject config, Long projectId, PluginManifest manifest) {
        if (projectId == null || manifest == null || manifest.getProjectMount() == null) {
            return;
        }
        try {
            JSONObject mountObj = enableService.loadMountForPlugin(projectId, manifest);
            PluginManifest.ProjectMountSpec injectSpec = manifest.getProjectMount();
            if (injectSpec.getInheritFrom() != null && !injectSpec.getInheritFrom().isBlank()
                    && injectSpec.getInject().isEmpty()) {
                PluginManifest owner = enableService.requireManifest(injectSpec.getInheritFrom());
                if (owner.getProjectMount() != null) {
                    injectSpec = owner.getProjectMount();
                }
            }
            ProjectMountSupport.applyMountToConfig(config, mountObj, injectSpec);
        } catch (PluginException ignored) {
            // Invoke 时若挂载缺失，沿用绑定内已注入配置
        }
    }

    /**
     * 将能力名与入参映射为 CLI 参数列表；优先说明书 argMap，再回落通用开关。
     */
    private List<String> buildCapabilityArgs(PluginInvokeRequest request, PluginManifest.CliSpec cli) {
        List<String> args = new ArrayList<>();
        args.add(request.getCapability());
        Map<String, Object> map = request.getArgs() == null ? Map.of() : request.getArgs();
        Map<String, String> argMap = cli == null ? Map.of() : cli.getArgMap();

        if (!argMap.isEmpty()) {
            for (Map.Entry<String, String> e : argMap.entrySet()) {
                Object val = map.get(e.getKey());
                if (val == null || String.valueOf(val).isBlank()) {
                    continue;
                }
                args.add(e.getValue());
                args.add(String.valueOf(val));
            }
        } else {
            Object id = map.get("saveId");
            if (id == null) {
                id = map.get("scenarioId");
            }
            if (id != null) {
                args.add("--id");
                args.add(String.valueOf(id));
            }
            Object storeKey = map.get("storeKey");
            if (storeKey != null && !String.valueOf(storeKey).isBlank()) {
                args.add("--db");
                args.add(String.valueOf(storeKey));
            }
        }

        Object force = map.get("force");
        if (Boolean.TRUE.equals(force) || "true".equalsIgnoreCase(String.valueOf(force))) {
            args.add("--force");
        }
        Object noReset = map.get("noReset");
        if (Boolean.TRUE.equals(noReset) || "true".equalsIgnoreCase(String.valueOf(noReset))) {
            args.add("--no-reset");
        }
        return args;
    }

    private String trim(String stdout, String stderr) {
        String s = (stdout == null ? "" : stdout.trim());
        if (s.isBlank()) {
            s = stderr == null ? "" : stderr.trim();
        }
        if (s.length() > 500) {
            return s.substring(0, 500);
        }
        return s;
    }
}
