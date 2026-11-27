package com.qualitest.flow.inspect;

import com.qualitest.plugin.manifest.PluginManifest;
import lombok.Getter;

import java.util.List;

/**
 * 一个已安装插件的说明书及其解析出的流检查目录。
 * 目录与平台侧求值共用同一份解析结果，避免重复读说明书。
 */
@Getter
public class PluginInspectionBundle {

    /** 已解析的插件说明书 */
    private final PluginManifest manifest;

    /** 该插件声明的流检查项；无声明时为空列表 */
    private final List<FlowInspectionDef> defs;

    /**
     * @param manifest 不可为空
     * @param defs     可空，空时按空列表保存
     */
    public PluginInspectionBundle(PluginManifest manifest, List<FlowInspectionDef> defs) {
        this.manifest = java.util.Objects.requireNonNull(manifest, "manifest");
        this.defs = defs == null ? List.of() : List.copyOf(defs);
    }

    /** 插件 id，取自说明书 */
    public String getPluginId() {
        return manifest.getId();
    }
}
