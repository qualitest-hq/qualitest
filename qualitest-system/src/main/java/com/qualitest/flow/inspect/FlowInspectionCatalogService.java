package com.qualitest.flow.inspect;

import com.qualitest.plugin.manifest.PluginManifest;
import com.qualitest.plugin.manifest.PluginManifestParser;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestPluginPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流检查目录服务。
 * 合并平台内置项与已安装插件声明的检查项，并按项目配置计算生效严重性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInspectionCatalogService {

    private final ITestPluginPackageService pluginPackageService;
    private final PluginManifestParser manifestParser;
    private final TestProjectMapper testProjectMapper;
    private final InspectionSeverityResolver severityResolver;

    /** 完整目录：内置项在前，其后按插件登记顺序追加 */
    public List<FlowInspectionDef> listAll() {
        List<FlowInspectionDef> out = new ArrayList<>(BuiltinFlowInspections.ALL);
        for (PluginInspectionBundle bundle : listPluginBundles()) {
            out.addAll(bundle.getDefs());
        }
        return out;
    }

    /**
     * 诊断码到目录定义的映射。
     * 同一码只保留先出现的一项。
     */
    public Map<String, FlowInspectionDef> defsByCode() {
        Map<String, FlowInspectionDef> map = new LinkedHashMap<>();
        for (FlowInspectionDef def : listAll()) {
            if (def != null && def.getCode() != null) {
                map.putIfAbsent(def.getCode(), def);
            }
        }
        return map;
    }

    /** 仅返回插件声明的检查项 */
    public List<FlowInspectionDef> listPluginDefs() {
        List<FlowInspectionDef> out = new ArrayList<>();
        for (PluginInspectionBundle bundle : listPluginBundles()) {
            out.addAll(bundle.getDefs());
        }
        return out;
    }

    /**
     * 列出已安装且未删除的插件包，并一次解析说明书与流检查声明。
     * 供目录展示与平台侧求值共用。某一包说明书损坏时跳过该包并打警告，不影响其它包。
     */
    public List<PluginInspectionBundle> listPluginBundles() {
        List<PluginInspectionBundle> out = new ArrayList<>();
        List<TestPluginPackage> packages = pluginPackageService.selectTestPluginPackageList(
                TestPluginPackage.builder().delStatus(0).build());
        if (packages == null) {
            return out;
        }
        for (TestPluginPackage pkg : packages) {
            if (pkg == null || pkg.getManifestJson() == null || pkg.getManifestJson().isBlank()) {
                continue;
            }
            try {
                PluginManifest manifest = manifestParser.parse(pkg.getManifestJson());
                List<FlowInspectionDef> defs = PluginInspectionManifestSupport.parseFromManifest(manifest);
                out.add(new PluginInspectionBundle(manifest, defs));
            } catch (RuntimeException e) {
                log.warn("跳过坏插件说明书 pluginId={} pkgId={}: {}",
                        pkg.getPluginId(), pkg.getTestPluginPackageId(), e.toString());
            }
        }
        return out;
    }

    /**
     * 返回带本项目生效严重性的完整目录，供项目设置页使用。
     *
     * @param testProjectId 项目 id；为空或项目不存在时生效严重性等于目录默认
     */
    public List<FlowInspectionCatalogItem> listCatalogForProject(Long testProjectId) {
        String profileJson = null;
        if (testProjectId != null) {
            TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
            if (project != null) {
                profileJson = project.getInspectionProfile();
            }
        }
        Map<String, InspectionConfiguredSeverity> overrides =
                InspectionProfileSupport.parseOverrides(profileJson);
        List<FlowInspectionCatalogItem> items = new ArrayList<>();
        for (FlowInspectionDef def : listAll()) {
            InspectionConfiguredSeverity effective =
                    severityResolver.resolve(def.getCode(), def, overrides);
            items.add(FlowInspectionCatalogItem.builder()
                    .code(def.getCode())
                    .shortCode(def.getShortCode())
                    .name(def.getName())
                    .category(def.getCategory())
                    .pluginId(def.getPluginId())
                    .evaluator(def.getEvaluator() != null ? def.getEvaluator().getCode() : null)
                    .capability(def.getCapability())
                    .locked(def.isLocked())
                    .defaultSeverity(def.getDefaultSeverity())
                    .effectiveSeverity(effective)
                    .build());
        }
        return items;
    }
}
