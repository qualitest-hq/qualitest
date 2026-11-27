package com.qualitest.flow.inspect;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.mount.ProjectMountSupport;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.params.TestPluginEnvBindingParams;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.result.TestPluginEnvBindingResult;
import com.qualitest.project.service.ITestPluginEnvBindingService;
import com.qualitest.project.service.ITestPluginProjectMountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行插件流检查中「平台侧」求值项，不启动插件进程。
 * 说明书与检查定义取自目录服务已解析的插件包，避免重复解析。
 */
@Component
@RequiredArgsConstructor
public class PlatformPluginInspectionEvaluator {

    private final FlowInspectionCatalogService catalogService;
    private final ITestPluginProjectMountService mountService;
    private final ITestPluginEnvBindingService bindingService;

    /**
     * 收集平台侧流检查问题。
     * 画布与开跑阶段均可调用。图或项目 id 为空时直接返回空列表。
     */
    public List<FlowIssue> evaluate(GraphJson graph, Long testProjectId, FlowIssue.Stage stage) {
        if (graph == null || testProjectId == null) {
            return List.of();
        }
        FlowIssue.Stage effective = stage != null ? stage : FlowIssue.Stage.CANVAS;
        List<FlowIssue> issues = new ArrayList<>();
        Set<String> enabledAnywhere = enabledPluginIdsInProject(testProjectId);
        boolean needsSnapshot = graphNeedsSnapshotPlugin(graph);
        for (PluginInspectionBundle bundle : catalogService.listPluginBundles()) {
            PluginManifest manifest = bundle.getManifest();
            for (FlowInspectionDef def : bundle.getDefs()) {
                InspectionEvaluator evaluator = def.getEvaluator();
                if (evaluator == null || !evaluator.isPlatform()) {
                    continue;
                }
                switch (evaluator) {
                    case PROJECT_MOUNT_REQUIRED -> {
                        // 项目已启用该插件，或图上开了执行前快照时才检查挂载
                        boolean relevant = enabledAnywhere.contains(def.getPluginId()) || needsSnapshot;
                        if (relevant) {
                            FlowIssue issue = evalProjectMountRequired(
                                    def, manifest, testProjectId, effective);
                            if (issue != null) {
                                issues.add(issue);
                            }
                        }
                    }
                    case GRAPH_REQUIRES_PLUGIN_ENABLED -> {
                        FlowIssue issue = evalRequiresPluginEnabled(
                                def, needsSnapshot, enabledAnywhere, effective);
                        if (issue != null) {
                            issues.add(issue);
                        }
                    }
                    default -> {
                        // Invoke 类检查不在此处理
                    }
                }
            }
        }
        return issues;
    }

    /**
     * 按说明书挂载声明校验项目挂载 JSON。
     * 校验失败时产出一条 ERROR 问题；通过则返回 null。
     */
    private FlowIssue evalProjectMountRequired(
            FlowInspectionDef def,
            PluginManifest manifest,
            Long testProjectId,
            FlowIssue.Stage stage) {
        if (manifest == null || manifest.getProjectMount() == null) {
            return null;
        }
        TestPluginProjectMount mount = mountService.selectTestPluginProjectMountOne(
                TestPluginProjectMountParams.builder()
                        .testProjectId(testProjectId)
                        .pluginId(def.getPluginId())
                        .build());
        JSONObject mountObj = parseMountJson(mount);
        try {
            ProjectMountSupport.requireValidMount(manifest.getProjectMount(), mountObj);
            return null;
        } catch (PluginException e) {
            String msg = StrUtil.blankToDefault(e.getMessage(), "挂载未完成");
            return FlowIssue.error(
                    def.getCode(),
                    stage,
                    null,
                    "插件「" + StrUtil.blankToDefault(manifest.getDisplayName(), def.getPluginId())
                            + "」" + msg);
        }
    }

    /**
     * 读取未删除挂载记录中的 JSON。
     * 无记录、已删除、内容空白或 JSON 损坏时返回 null，后续按挂载缺失处理。
     */
    private static JSONObject parseMountJson(TestPluginProjectMount mount) {
        if (mount == null || mount.getDelStatus() == null || mount.getDelStatus() != 0) {
            return null;
        }
        String json = mount.getMountJson();
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 图上开了执行前快照，但项目没有任何环境启用该插件时产出 ERROR。
     */
    private FlowIssue evalRequiresPluginEnabled(
            FlowInspectionDef def,
            boolean needsSnapshot,
            Set<String> enabledAnywhere,
            FlowIssue.Stage stage) {
        if (!needsSnapshot || enabledAnywhere.contains(def.getPluginId())) {
            return null;
        }
        return FlowIssue.error(
                def.getCode(),
                stage,
                null,
                "图中启用了执行前快照，但项目尚无环境启用插件「" + def.getPluginId() + "」");
    }

    /** 图中是否存在节点开启了执行前快照 */
    private static boolean graphNeedsSnapshotPlugin(GraphJson graph) {
        if (graph.getNodes() == null) {
            return false;
        }
        for (GraphNode node : graph.getNodes()) {
            if (node == null || node.getData() == null) {
                continue;
            }
            Object raw = node.getData().get("snapshotBefore");
            if (Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw))) {
                return true;
            }
        }
        return false;
    }

    /** 收集本项目任意环境下已启用的插件 id */
    private Set<String> enabledPluginIdsInProject(Long testProjectId) {
        Set<String> ids = new HashSet<>();
        List<TestPluginEnvBindingResult> list = bindingService.selectTestPluginEnvBindingResultList(
                TestPluginEnvBindingParams.builder()
                        .testProjectId(testProjectId)
                        .enabled(1)
                        .build());
        if (list == null) {
            return ids;
        }
        for (TestPluginEnvBindingResult r : list) {
            if (r != null && r.getPluginId() != null) {
                ids.add(r.getPluginId());
            }
        }
        return ids;
    }
}
