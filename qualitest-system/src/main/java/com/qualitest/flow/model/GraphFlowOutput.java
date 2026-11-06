package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试流图级对外产出变量声明，保存在 graph_json.meta.flowOutputs。
 * <p>
 * 子流节点未配置 outputs 时，按 name 作为子流程变量名与父流程同名键写回。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphFlowOutput {

    /**
     * 流程变量键名
     */
    private String name;

    /**
     * 可选说明文案
     */
    private String description;
}
