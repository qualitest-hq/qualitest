package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphJson;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Web 端「单 Staging 单元确认」的请求体。
 * <p>
 * 服务端在内存中完成 draft 合并、规范化、单单元过滤、合并与图校验，不写入 test_flow 表。
 */
@Getter
@Setter
public class FlowDesignPatchConfirmRequest {

    /** 当前测试项目 id；HTTP(project) 节点 API 归属校验时使用 */
    private Long testProjectId;

    /**
     * 用户当前画布的 graph_json。
     * 含已 confirm 内容；未 confirm 的 Staging 对象由前端预先注入后再提交。
     */
    private GraphJson graphJson;

    /** AI 通过 submit 工具返回的完整增量 patch */
    private FlowDesignPatch patch;

    /**
     * 待确认的单个 Staging 单元 id。
     * <p>
     * 键名约定：addNode:{id}、updateNode:{id}、addEdge:{id}、updateEdge:{id}、
     * deleteNode:{id}、deleteEdge:{id}、scenario:activeScenarioId、
     * addScenario:{id}、updateScenario:{id}、deleteScenario:{id}。
     */
    private String unitId;

    /**
     * 可选。前端在 Staging 态编辑后的 draft，在规范化前合并进 patch 对应项。
     * 结构为 GraphNode / GraphEdge / GraphRunScenario 的 JSON 子集。
     */
    private Object draftOverride;

    /**
     * 本会话内已确认通过的 Staging 单元 id 列表。
     * 用于 addEdge 等依赖校验：patch 内 addNode 端点须先出现在此列表中。
     */
    private List<String> confirmedUnitIds = new ArrayList<>();

    /**
     * 本会话内已拒绝的 Staging 单元 id 列表。
     * 与 confirmedUnitIds 一起判定本 patch 是否仍有未决单元，以决定是否延后拓扑结构校验。
     */
    private List<String> rejectedUnitIds = new ArrayList<>();
}
