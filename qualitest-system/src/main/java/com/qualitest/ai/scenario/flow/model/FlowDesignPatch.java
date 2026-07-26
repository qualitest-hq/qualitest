package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对测试流画布的增量修改建议（patch）。
 * <p>
 * 前端 Diff 预览后，用户勾选接受的项再合并进编辑态；
 * 服务端规范化时会补全 id、校验 API、预合并图校验。
 */
@Getter
@Setter
public class FlowDesignPatch {

    /** 新增节点列表 */
    private List<GraphNode> addNodes = new ArrayList<>();

    /** 修改已有节点（增量改图时使用） */
    private List<GraphNode> updateNodes = new ArrayList<>();

    /** 新增边列表 */
    private List<GraphEdge> addEdges = new ArrayList<>();

    /** 修改已有边 */
    private List<GraphEdge> updateEdges = new ArrayList<>();

    /** 建议删除的节点/边 id，需用户确认后前端执行删除 */
    private SuggestedDeletes suggestedDeletes = new SuggestedDeletes();

    /** 运行场景 meta 的增量修改 */
    private FlowDesignScenarioPatch scenarioPatch;

    /** 中文流程说明，供用户审阅 */
    private String summary;

    /** 建议删除项 */
    @Getter
    @Setter
    public static class SuggestedDeletes {
        /** 建议删除的节点 id */
        private List<String> nodeIds = new ArrayList<>();
        /** 建议删除的边 id */
        private List<String> edgeIds = new ArrayList<>();
    }
}
