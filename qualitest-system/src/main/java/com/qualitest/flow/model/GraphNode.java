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
 * {@code data} 按 {@code type} 存放业务配置，不含画布 UI 状态字段。
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
     * 节点类型：{@code http} / {@code assert} / {@code delay} / {@code condition} / {@code assign}
     */
    private String type;

    /**
     * 画布坐标；执行不读
     */
    private GraphNodePosition position;

    /**
     * 业务配置；不得含 {@code selected}、{@code dragging} 等 UI 字段
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
}
