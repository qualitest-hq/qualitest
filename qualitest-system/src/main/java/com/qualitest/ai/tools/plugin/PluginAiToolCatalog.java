package com.qualitest.ai.tools.plugin;

import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.manifest.PluginAiTool;
import com.qualitest.plugin.manifest.PluginAiToolsSupport;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectEnvMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 造流 / MCP 场景下「项目已启用插件所贡献的 AI 工具」目录。
 * <p>
 * 启用判定：项目下任一环境有 enabled 绑定即算启用。
 * 工具来自说明书 {@code ai.tools[]}；同名工具跨插件时保留先出现的（按启用 pluginId 保序）。
 */
@Component
@RequiredArgsConstructor
public class PluginAiToolCatalog {

    private final PluginEnableService pluginEnableService;
    private final TestProjectEnvMapper testProjectEnvMapper;

    /**
     * 列出项目已启用插件贡献的全部 AI 工具（去重保序）。
     */
    public List<ContributedTool> listContributedTools(Long testProjectId) {
        if (testProjectId == null) {
            return List.of();
        }
        Set<String> enabledIds = enabledPluginIdsOnProject(testProjectId);
        if (enabledIds.isEmpty()) {
            return List.of();
        }
        List<ContributedTool> out = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (String pluginId : enabledIds) {
            PluginManifest manifest = findManifest(pluginId);
            if (manifest == null) {
                continue;
            }
            for (PluginAiTool tool : PluginAiToolsSupport.listTools(manifest)) {
                if (!seenNames.add(tool.getName())) {
                    continue;
                }
                out.add(new ContributedTool(pluginId, manifest.getDisplayName(), tool));
            }
        }
        return List.copyOf(out);
    }

    /**
     * 按工具名点查已启用贡献；未启用或未声明时 null。
     * <p>
     * 遍历启用集，命中即返回（与 {@link #listContributedTools} 同序：先启用先得），不先拼整表。
     */
    public ContributedTool findContributedTool(Long testProjectId, String toolName) {
        if (testProjectId == null || toolName == null || toolName.isBlank()) {
            return null;
        }
        String name = toolName.trim();
        Set<String> enabledIds = enabledPluginIdsOnProject(testProjectId);
        if (enabledIds.isEmpty()) {
            return null;
        }
        for (String pluginId : enabledIds) {
            PluginManifest manifest = findManifest(pluginId);
            if (manifest == null) {
                continue;
            }
            PluginAiTool tool = PluginAiToolsSupport.findByName(manifest, name);
            if (tool != null) {
                return new ContributedTool(pluginId, manifest.getDisplayName(), tool);
            }
        }
        return null;
    }

    /** 按 pluginId 取已登记说明书；未登记或解析失败时 null */
    private PluginManifest findManifest(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return null;
        }
        try {
            return pluginEnableService.requireManifest(pluginId.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 汇总项目所有环境下 enabled=1 的 pluginId（去重保序） */
    private Set<String> enabledPluginIdsOnProject(Long testProjectId) {
        List<TestProjectEnv> envs = testProjectEnvMapper.selectTestProjectEnvList(
                TestProjectEnv.builder().testProjectId(testProjectId).build());
        if (envs == null || envs.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (TestProjectEnv env : envs) {
            if (env == null || env.getTestProjectEnvId() == null) {
                continue;
            }
            for (TestPluginEnvBinding b : pluginEnableService.listEnabledBindings(env.getTestProjectEnvId())) {
                if (b != null && b.getPluginId() != null && !b.getPluginId().isBlank()) {
                    ids.add(b.getPluginId().trim());
                }
            }
        }
        return ids;
    }

    /**
     * 一条插件贡献的 AI 工具：含所属 pluginId、展示名与工具声明。
     */
    public record ContributedTool(
            String pluginId,
            String displayName,
            PluginAiTool tool
    ) {
    }
}
