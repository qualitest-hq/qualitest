package com.qualitest.plugin.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 插件说明书里一颗可落图画布步骤（ui.palette.nodes 数组的一项）。
 * <p>
 * 落图后写入节点 data：contributionId←id，capability←capability，config 按 argsSchema 约束。
 * 引擎节点类型固定为 pluginAction，不为本插件新增 FlowNodeType。
 */
@Getter
@Builder
public class PluginPaletteNode {

    /** 短贡献 id，写入图节点 data.contributionId */
    private final String id;
    /** 画布与工具上的展示名 */
    private final String label;
    /** 本插件可 Invoke 的能力短名，须属于说明书 capabilities 列表 */
    private final String capability;
    /**
     * 本步骤 data.config 的 JSON Schema。
     * 说明书优先写 argsSchema；若只有 configSchema 字段，解析时也会收成 argsSchema。
     */
    private final Map<String, Object> argsSchema;

    /** 返回 argsSchema；未声明时为空 Map（不可变视图） */
    public Map<String, Object> getArgsSchema() {
        return argsSchema == null ? Collections.emptyMap() : argsSchema;
    }

    /** 是否声明了非空 argsSchema（AI 造流要求必须有，图校验仅在有 schema 时检查 config） */
    public boolean hasArgsSchema() {
        return argsSchema != null && !argsSchema.isEmpty();
    }
}
