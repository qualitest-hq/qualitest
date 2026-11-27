package com.qualitest.flow.inspect;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.manifest.PluginManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从插件说明书的 {@code ui.inspections} 解析流检查目录项。
 */
public final class PluginInspectionManifestSupport {

    private PluginInspectionManifestSupport() {}

    /**
     * 拼出插件检查的生效诊断码：{@code 插件id/短码}。
     * 两侧均不可为空或空白。
     */
    public static String qualifiedCode(String pluginId, String shortCode) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(shortCode, "shortCode");
        if (StrUtil.hasBlank(pluginId, shortCode)) {
            throw new IllegalArgumentException("pluginId/shortCode 不能为空");
        }
        return pluginId + "/" + shortCode;
    }

    /**
     * 从已解析说明书中读取流检查声明。
     * 说明书或插件 id 缺失时返回空列表。
     */
    public static List<FlowInspectionDef> parseFromManifest(PluginManifest manifest) {
        if (manifest == null || StrUtil.isBlank(manifest.getId())) {
            return List.of();
        }
        return parseFromUi(manifest.getId(), manifest.getUi());
    }

    /**
     * 从说明书 {@code ui} 对象解析检查项。
     * <ul>
     *   <li>{@code inspections} 必须是对象数组，否则整段忽略</li>
     *   <li>未写 {@code evaluator} 时按 Invoke 处理</li>
     *   <li>无法识别的求值方式、或 Invoke 却未写能力名的项会被跳过</li>
     *   <li>未写默认严重性时按 ERROR</li>
     * </ul>
     */
    public static List<FlowInspectionDef> parseFromUi(String pluginId, Map<String, Object> ui) {
        if (StrUtil.isBlank(pluginId) || ui == null || ui.isEmpty()) {
            return List.of();
        }
        // inspections 约定为对象数组；JSONArray 本身也是 List
        Object raw = ui.get("inspections");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        JSONArray arr = JSONArray.from(list);
        List<FlowInspectionDef> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String shortCode = StrUtil.trimToNull(o.getString("code"));
            if (shortCode == null) {
                continue;
            }
            InspectionConfiguredSeverity defSev =
                    InspectionConfiguredSeverity.fromCode(o.getString("defaultSeverity"));
            if (defSev == null) {
                defSev = InspectionConfiguredSeverity.ERROR;
            }
            String evaluatorRaw = o.getString("evaluator");
            InspectionEvaluator evaluator = StrUtil.isBlank(evaluatorRaw)
                    ? InspectionEvaluator.INVOKE
                    : InspectionEvaluator.fromCode(evaluatorRaw);
            if (evaluator == null) {
                // 未知求值方式不进目录，避免配置了却永远不跑
                continue;
            }
            String capability = StrUtil.trimToNull(o.getString("capability"));
            if (evaluator == InspectionEvaluator.INVOKE && capability == null) {
                continue;
            }
            out.add(FlowInspectionDef.builder()
                    .code(qualifiedCode(pluginId, shortCode))
                    .shortCode(shortCode)
                    .name(StrUtil.blankToDefault(StrUtil.trim(o.getString("name")), shortCode))
                    .category(StrUtil.blankToDefault(StrUtil.trim(o.getString("category")), "插件"))
                    .defaultSeverity(defSev)
                    .locked(Boolean.TRUE.equals(o.getBoolean("locked")))
                    .pluginId(pluginId)
                    .evaluator(evaluator)
                    .capability(capability)
                    .build());
        }
        return out;
    }
}
