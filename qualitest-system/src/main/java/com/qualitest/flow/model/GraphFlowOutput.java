package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 子流图对外产出变量声明，存于 {@code graph_json.meta.flowOutputs}（非数据库字段）。
 * <p>
 * 子流 Run 结束后，父流程 subflow 节点的 outputs 映射默认按 {@code name} 与 {@code flowKey} 一一对应；
 * 供画布展示、AI get_subflow_detail 与校验提示使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphFlowOutput {

    /**
     * flow 变量键名
     */
    private String name;

    /**
     * 说明（可选，模板/AI 用）
     */
    private String description;
}
