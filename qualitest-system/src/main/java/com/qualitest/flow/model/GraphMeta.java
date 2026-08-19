package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 图元数据：画布状态、运行场景与流程返回值声明。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphMeta {

    /**
     * 图 schema 版本；缺失视为 {@link GraphSchemaVersions#DEFAULT}（当前为 1）。
     */
    private Integer schemaVersion;

    /**
     * 画布视口；默认 {@code { x:40, y:40, zoom:1 }}
     */
    private GraphViewport viewport;

    /**
     * 布局模式；固定 {@code manual}
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
     * 流程对外暴露的 flow 返回值声明；子流节点 outputs 为空时用作默认映射
     */
    @Builder.Default
    private List<GraphFlowOutput> flowOutputs = new ArrayList<>();
}
