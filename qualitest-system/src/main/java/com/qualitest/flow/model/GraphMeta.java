package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 图元数据：画布状态、运行场景、入参定义/预设、以及流程返回值声明。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphMeta {

    /**
     * 图 schema 版本；当前写入 {@link GraphSchemaVersions#CURRENT}（为 1）。
     */
    private Integer schemaVersion;

    /**
     * 画布视口；默认 {@code { x:40, y:40, zoom:1 }}
     */
    private GraphViewport viewport;

    /**
     * 这条流的排版方式，只记方式，不记连线拐点。
     * default：从左往右分层并折行。
     * horizontal：横向分层，不折行。
     * horizontal-wrap：横向分层并折行。
     * vertical：自上而下分层。
     */
    private String layout;

    /**
     * 唯一开始节点 id 缓存；执行仍以拓扑入度为准，此字段仅辅助画布
     */
    private String startNodeId;

    /**
     * 默认选中的场景 id（画布场景运行时使用）
     */
    private String activeScenarioId;

    /**
     * 运行场景列表（至少 1 条）
     */
    @Builder.Default
    private List<GraphRunScenario> scenarios = new ArrayList<>();

    /**
     * 入参契约：声明字段，以及是否开放 HTTP 入站 path。
     * 开放入站终态回包只读运行时 {@code out}。
     */
    private GraphInputSpec inputSpec;

    /**
     * 旧版入参预设列表（兼容）。
     * 新图以场景 {@code inSeed} 为准；仅当场景尚无 {@code inSeed} 字段时开跑才回落读本列表。
     */
    @Builder.Default
    private List<GraphInputPreset> inputPresets = new ArrayList<>();

    /**
     * 旧版活动入参预设 id（兼容）；新图不再作为画布主选择。
     */
    private String activeInputPresetId;

    /**
     * 流程对外暴露的 flow 返回值声明；子流节点 outputs 为空时用作默认映射
     */
    @Builder.Default
    private List<GraphFlowOutput> flowOutputs = new ArrayList<>();
}
