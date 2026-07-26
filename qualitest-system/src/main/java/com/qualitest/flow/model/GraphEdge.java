package com.qualitest.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试流图边，描述节点间的有向连接。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {

    private String id;

    /**
     * 源节点 id
     */
    private String source;

    /**
     * 目标节点 id
     */
    private String target;

    /**
     * 画布展示标签（可选）；拓扑遍历不读
     */
    private String label;
}
