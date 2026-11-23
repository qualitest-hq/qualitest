package com.qualitest.plugin.enable;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.plugin.PluginDestructiveGate;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.channel.EnvExecChannelSupport;
import com.qualitest.plugin.channel.ExecAgentChannel;
import com.qualitest.plugin.isolation.UiSlotIsolation;
import com.qualitest.plugin.manifest.PluginConfigSchemaValidator;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.manifest.PluginManifestParser;
import com.qualitest.plugin.mount.ProjectMountSupport;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.TestPluginEnvBindingParams;
import com.qualitest.project.params.TestPluginPackageParams;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.service.ITestPluginEnvBindingService;
import com.qualitest.project.service.ITestPluginPackageService;
import com.qualitest.project.service.ITestPluginProjectMountService;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 环境级插件启用与停用。
 * 启用序：包登记 → 说明书 → requires → ui-slot 互斥 → 项目挂载 → 挂载注入 → configSchema → 总闸 → 通道探活。
 */
@Service
@RequiredArgsConstructor
public class PluginEnableService {

    private final ITestPluginPackageService packageService;
    private final ITestPluginEnvBindingService bindingService;
    private final ITestPluginProjectMountService mountService;
    private final ITestProjectEnvService envService;
    private final PluginManifestParser manifestParser;
    private final PluginConfigSchemaValidator configSchemaValidator;
    private final ExecAgentChannel execAgentChannel;

    /**
     * 启用插件并写入环境绑定。
     *
     * @param configJson 环境侧附加配置 JSON，可为 null 或空对象字符串
     * @return 启用后的绑定行
     */
    @Transactional(rollbackFor = Exception.class)
    public TestPluginEnvBinding enable(Long testProjectEnvId, String pluginId, String configJson) {
        TestProjectEnv env = requireEnv(testProjectEnvId);
        TestPluginPackage pkg = packageService.selectTestPluginPackageOne(
                TestPluginPackageParams.builder().pluginId(pluginId).build());
        if (pkg == null || (pkg.getDelStatus() != null && pkg.getDelStatus() != 0)) {
            throw new PluginException(PluginErrorCode.PACKAGE_NOT_FOUND, "插件未登记: " + pluginId);
        }
        PluginManifest manifest = manifestParser.parse(pkg.getManifestJson());

        requireDependencies(testProjectEnvId, manifest);
        requireUiSlotFree(testProjectEnvId, manifest);

        JSONObject config = parseConfig(configJson);
        applyProjectMount(env.getTestProjectId(), manifest, config);
        configSchemaValidator.validate(manifest.getConfigSchema(), config);
        requireGates(env, manifest);

        if (manifest.isChannelProvider()) {
            probeChannelProvider(config);
        }

        String normalizedConfig = config.isEmpty() ? "{}" : config.toJSONString();
        TestPluginEnvBinding existing = bindingService.selectTestPluginEnvBindingOne(
                TestPluginEnvBindingParams.builder()
                        .testProjectEnvId(testProjectEnvId)
                        .pluginId(pluginId)
                        .build());
        if (existing == null) {
            TestPluginEnvBinding row = TestPluginEnvBinding.builder()
                    .testPluginEnvBindingId(IdUtil.getSnowflakeNextId())
                    .testProjectId(env.getTestProjectId())
                    .testProjectEnvId(testProjectEnvId)
                    .pluginId(pluginId)
                    .enabled(1)
                    .configJson(normalizedConfig)
                    .delStatus(0)
                    .build();
            row.setCreateTime(DateUtils.getNowDate());
            bindingService.insertTestPluginEnvBinding(row);
            return row;
        }
        existing.setEnabled(1);
        existing.setConfigJson(normalizedConfig);
        existing.setDelStatus(0);
        existing.setUpdateTime(DateUtils.getNowDate());
        bindingService.updateTestPluginEnvBinding(existing);
        return existing;
    }

    /**
     * 停用插件：将绑定行 enabled 置 0，保留配置便于再次启用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long testProjectEnvId, String pluginId) {
        TestPluginEnvBinding existing = bindingService.selectTestPluginEnvBindingOne(
                TestPluginEnvBindingParams.builder()
                        .testProjectEnvId(testProjectEnvId)
                        .pluginId(pluginId)
                        .build());
        if (existing == null) {
            return;
        }
        existing.setEnabled(0);
        existing.setUpdateTime(DateUtils.getNowDate());
        bindingService.updateTestPluginEnvBinding(existing);
    }

    /**
     * 判断环境是否已启用某插件（未删除且 enabled=1）。
     */
    public boolean isEnabled(Long testProjectEnvId, String pluginId) {
        TestPluginEnvBinding existing = bindingService.selectTestPluginEnvBindingOne(
                TestPluginEnvBindingParams.builder()
                        .testProjectEnvId(testProjectEnvId)
                        .pluginId(pluginId)
                        .build());
        return existing != null
                && existing.getDelStatus() != null && existing.getDelStatus() == 0
                && existing.getEnabled() != null && existing.getEnabled() == 1;
    }

    /**
     * 取已启用绑定；未启用或不存在时抛出 PROVIDER_NOT_FOUND。
     */
    public TestPluginEnvBinding requireEnabledBinding(Long testProjectEnvId, String pluginId) {
        TestPluginEnvBinding existing = bindingService.selectTestPluginEnvBindingOne(
                TestPluginEnvBindingParams.builder()
                        .testProjectEnvId(testProjectEnvId)
                        .pluginId(pluginId)
                        .build());
        if (existing == null || existing.getDelStatus() == null || existing.getDelStatus() != 0
                || existing.getEnabled() == null || existing.getEnabled() != 1) {
            throw new PluginException(PluginErrorCode.PROVIDER_NOT_FOUND, "插件未启用: " + pluginId);
        }
        return existing;
    }

    /**
     * 按插件 id 读取登记包并解析说明书；未登记则抛 PACKAGE_NOT_FOUND。
     */
    public PluginManifest requireManifest(String pluginId) {
        TestPluginPackage pkg = packageService.selectTestPluginPackageOne(
                TestPluginPackageParams.builder().pluginId(pluginId).build());
        if (pkg == null || (pkg.getDelStatus() != null && pkg.getDelStatus() != 0)) {
            throw new PluginException(PluginErrorCode.PACKAGE_NOT_FOUND, "插件未登记: " + pluginId);
        }
        return manifestParser.parse(pkg.getManifestJson());
    }

    /**
     * 读取本包（或 inheritFrom）对应的项目挂载并校验；无 projectMount 声明时返回 null。
     */
    public JSONObject loadMountForPlugin(Long projectId, PluginManifest manifest) {
        if (manifest == null || manifest.getProjectMount() == null) {
            return null;
        }
        PluginManifest.ProjectMountSpec spec = manifest.getProjectMount();
        String mountPluginId = spec.getInheritFrom() != null && !spec.getInheritFrom().isBlank()
                ? spec.getInheritFrom()
                : manifest.getId();
        PluginManifest mountOwner = mountPluginId.equals(manifest.getId())
                ? manifest
                : requireManifest(mountPluginId);
        PluginManifest.ProjectMountSpec validateSpec = mountOwner.getProjectMount() != null
                ? mountOwner.getProjectMount()
                : spec;
        TestPluginProjectMount mount = mountService.selectTestPluginProjectMountOne(
                TestPluginProjectMountParams.builder()
                        .testProjectId(projectId)
                        .pluginId(mountPluginId)
                        .build());
        if (mount == null || mount.getMountJson() == null || mount.getMountJson().isBlank()) {
            throw new PluginException(PluginErrorCode.PROJECT_MOUNT_REQUIRED,
                    "请先在项目设置挂载插件: " + mountPluginId);
        }
        JSONObject mountObj = JSON.parseObject(mount.getMountJson());
        if (mountObj == null) {
            throw new PluginException(PluginErrorCode.PROJECT_MOUNT_REQUIRED,
                    "请先在项目设置挂载插件: " + mountPluginId);
        }
        return ProjectMountSupport.requireValidMount(validateSpec, mountObj);
    }

    private void applyProjectMount(Long projectId, PluginManifest manifest, JSONObject config) {
        if (manifest.getProjectMount() == null) {
            return;
        }
        JSONObject mountObj = loadMountForPlugin(projectId, manifest);
        PluginManifest.ProjectMountSpec injectSpec = manifest.getProjectMount();
        if (injectSpec.getInheritFrom() != null && !injectSpec.getInheritFrom().isBlank()
                && injectSpec.getInject().isEmpty()) {
            // 仅 inheritFrom 时，沿用被继承包的 inject 规则
            PluginManifest owner = requireManifest(injectSpec.getInheritFrom());
            if (owner.getProjectMount() != null) {
                injectSpec = owner.getProjectMount();
            }
        }
        ProjectMountSupport.applyMountToConfig(config, mountObj, injectSpec);
    }

    private void requireDependencies(Long envId, PluginManifest manifest) {
        for (String req : manifest.getRequires()) {
            if (!isEnabled(envId, req)) {
                throw new PluginException(PluginErrorCode.REQUIRES_UNSATISFIED,
                        "启用前请先启用依赖插件: " + req);
            }
        }
    }

    /**
     * 启用互斥仅认 ui-slot:：同环境另一 pluginId 已占用相同槽则拒。
     * 非 ui-slot 的 isolation 标签忽略（不做类别 mutex）。
     */
    private void requireUiSlotFree(Long envId, PluginManifest manifest) {
        List<String> mySlots = UiSlotIsolation.slotsOf(manifest);
        if (mySlots.isEmpty()) {
            return;
        }
        Map<String, List<String>> occupied = new HashMap<>();
        for (TestPluginEnvBinding binding : listEnabledBindings(envId)) {
            if (binding == null || binding.getPluginId() == null) {
                continue;
            }
            try {
                occupied.put(binding.getPluginId(), UiSlotIsolation.slotsOf(requireManifest(binding.getPluginId())));
            } catch (PluginException e) {
                // 坏说明书跳过
            }
        }
        UiSlotIsolation.requireFree(manifest.getId(), mySlots, occupied);
    }

    private void probeChannelProvider(JSONObject config) {
        String baseUrl = EnvExecChannelSupport.resolveBaseUrl(config);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID,
                    "通道插件须配置 baseUrl");
        }
        config.put("baseUrl", baseUrl.trim());
        String token = EnvExecChannelSupport.resolveToken(config);
        if (token != null) {
            config.put("token", token.trim());
        }
        execAgentChannel.ping(baseUrl.trim(), token == null ? "" : token.trim());
    }

    /** 校验环境存在且未软删。 */
    private TestProjectEnv requireEnv(Long testProjectEnvId) {
        TestProjectEnv env = envService.selectTestProjectEnvById(testProjectEnvId);
        if (env == null || (env.getDelStatus() != null && env.getDelStatus() != 0)) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "环境不存在");
        }
        return env;
    }

    /**
     * 说明书任一项能力为破坏性总闸时，要求环境 allowDestructiveReset=1，否则拒绝启用。
     */
    private void requireGates(TestProjectEnv env, PluginManifest manifest) {
        Set<String> gates = new HashSet<>();
        for (PluginManifest.Capability c : manifest.getCapabilities()) {
            gates.add(c.resolvedGate());
        }
        if (gates.contains("destructive") && !PluginDestructiveGate.isAllowed(env)) {
            throw new PluginException(PluginErrorCode.GATE_DENIED, "启用本插件须开启环境破坏性总闸");
        }
    }

    /** 解析环境配置 JSON；空则返回空对象；非法 JSON 抛 CONFIG_SCHEMA_INVALID。 */
    private JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject obj = JSON.parseObject(configJson);
            return obj == null ? new JSONObject() : obj;
        } catch (Exception e) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "config JSON 无效", e);
        }
    }

    /** 列出环境已启用绑定（供 UI / Hook 使用）。 */
    public List<TestPluginEnvBinding> listEnabledBindings(Long testProjectEnvId) {
        return bindingService.selectTestPluginEnvBindingList(
                TestPluginEnvBinding.builder()
                        .testProjectEnvId(testProjectEnvId)
                        .enabled(1)
                        .build());
    }
}
