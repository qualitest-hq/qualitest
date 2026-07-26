package com.qualitest.flow.model;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试流图持久化根对象，对应 {@code test_flow.graph_json} 列及导入导出 JSON。
 * <p>
 * 含节点列表、边列表与元数据（画布状态 + 运行场景）。
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class GraphJson {

    @Builder.Default
    private List<GraphNode> nodes = new ArrayList<>();

    @Builder.Default
    private List<GraphEdge> edges = new ArrayList<>();

    private GraphMeta meta;

    /**
     * 从 JSON 字符串解析为 {@link GraphJson}（如从 {@code test_flow.graph_json} 读取）。
     *
     * @param json 完整 graph_json 文本
     * @return 解析后的图模型
     */
    public static GraphJson parse(String json) {
        return JSON.parseObject(json, GraphJson.class);
    }

    /**
     * 序列化为 JSON 字符串，用于保存、Run 快照等。
     *
     * @return JSON 文本
     */
    public String toJsonString() {
        return JSON.toJSONString(this);
    }
}
