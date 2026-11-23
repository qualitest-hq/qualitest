package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试流图节点，持久化与执行器共用。
 * <p>
 * 壳字段：{@code id} / {@code type} / {@code name} / {@code position} / {@code data}。
 * {@code name} 必填（展示名写在节点壳）；{@code data} 按 {@code type} 存业务配置，不含画布 UI 状态字段。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNode {

    /**
     * 图内唯一 id；Run 报告与 {@code step_details} 引用此 id
     */
    private String id;

    /**
     * 节点类型：http / assert / delay / condition / assign / script / subflow / input /
     * scenario-entry / http-entry / subflow-entry / http-return / subflow-return
     */
    private String type;

    /**
     * 节点展示名（必填）；Run 步骤名与画布标题共用
     */
    private String name;

    /**
     * 画布坐标；执行不读
     */
    private GraphNodePosition position;

    /**
     * 业务配置；展示名写在节点顶层 {@code name}；不含 {@code selected}、{@code dragging} 等画布 UI 状态
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
}
