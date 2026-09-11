package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Web 造流助手的分类型画布提交工具。
 * <p>
 * 模型每次调用只改一类对象（加/改某型节点、加/改边、删节点或边或场景、加/改场景）。
 * 本类把工具参数拼成只含一个 Staging 单元的增量 patch，再交给共用提交逻辑做校验与累积。
 * 不直接写库；成功后的单元进入本轮累积 patch，由前端面板确认后才落盘。
 */
public class SubmitFlowDesignUnitTool implements QualitestTool {

    /** 工具名，如 submit_add_http_node */
    private final String name;
    /** 把本次 arguments 转成单单元 patch */
    private final Function<Map<String, Object>, FlowDesignPatch> patchBuilder;
    /** 单单元校验、Capture 写入、推进工作图 */
    private final FlowDesignUnitSubmitSupport support;

    public SubmitFlowDesignUnitTool(String name,
                                    Function<Map<String, Object>, FlowDesignPatch> patchBuilder,
                                    FlowDesignUnitSubmitSupport support) {
        this.name = name;
        this.patchBuilder = patchBuilder;
        this.support = support;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * 解析参数 → 构建单单元 patch → 提交。
     * 参数不合法时立刻返回 error JSON，不进入 Capture。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        FlowDesignPatch patch;
        try {
            patch = patchBuilder.apply(arguments != null ? arguments : Map.of());
        } catch (IllegalArgumentException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("无法解析参数: " + e.getMessage());
        }
        if (patch == null) {
            return FlowDesignToolSupport.errorJson("submit 参数为空");
        }
        return support.submitUnit(patch, ctx, name);
    }

    /**
     * 新增指定类型节点。
     * type 由注册时写死（http/assert/…），缺 id 时生成临时短名 n_*。
     */
    public static FlowDesignPatch addNode(String type, Map<String, Object> args) {
        return withSummary(args, patch -> patch.getAddNodes().add(buildNode(type, args, true)));
    }

    /**
     * 修改已有节点；必须带 id。
     * type 写死进节点，供后续按类型规范化 data。
     */
    public static FlowDesignPatch updateNode(String type, Map<String, Object> args) {
        requireArg(args, "id", "update 须提供节点 id");
        return withSummary(args, patch -> patch.getUpdateNodes().add(buildNode(type, args, false)));
    }

    /** 新增边；缺 id 时生成临时短名 e_*。 */
    public static FlowDesignPatch addEdge(Map<String, Object> args) {
        return withSummary(args, patch -> patch.getAddEdges().add(buildEdge(args, true)));
    }

    /** 修改已有边；必须带 id。 */
    public static FlowDesignPatch updateEdge(Map<String, Object> args) {
        requireArg(args, "id", "update_edge 须提供边 id");
        return withSummary(args, patch -> patch.getUpdateEdges().add(buildEdge(args, false)));
    }

    /** 建议删除节点（写入 suggestedDeletes.nodeIds）。 */
    public static FlowDesignPatch deleteNode(Map<String, Object> args) {
        String id = requireArg(args, "nodeId", "缺少 nodeId");
        return withSummary(args, patch -> patch.getSuggestedDeletes().getNodeIds().add(id));
    }

    /** 建议删除边（写入 suggestedDeletes.edgeIds）。 */
    public static FlowDesignPatch deleteEdge(Map<String, Object> args) {
        String id = requireArg(args, "edgeId", "缺少 edgeId");
        return withSummary(args, patch -> patch.getSuggestedDeletes().getEdgeIds().add(id));
    }

    /** 建议删除运行场景。 */
    public static FlowDesignPatch deleteScenario(Map<String, Object> args) {
        String id = requireArg(args, "scenarioId", "缺少 scenarioId");
        return withSummary(args, patch -> {
            FlowDesignScenarioPatch sp = new FlowDesignScenarioPatch();
            sp.setDeleteScenarioIds(List.of(id));
            patch.setScenarioPatch(sp);
        });
    }

    /** 新增运行场景；缺 id 时生成临时短名 sc_*。 */
    public static FlowDesignPatch addScenario(Map<String, Object> args) {
        return withSummary(args, patch -> {
            FlowDesignScenarioPatch sp = new FlowDesignScenarioPatch();
            sp.getAddScenarios().add(buildScenario(args, true));
            patch.setScenarioPatch(sp);
        });
    }

    /** 修改已有运行场景；必须带 id。 */
    public static FlowDesignPatch updateScenario(Map<String, Object> args) {
        requireArg(args, "id", "update_scenario 须提供 scenario id");
        return withSummary(args, patch -> {
            FlowDesignScenarioPatch sp = new FlowDesignScenarioPatch();
            sp.getUpdateScenarios().add(buildScenario(args, false));
            patch.setScenarioPatch(sp);
        });
    }

    /** 创建空 patch，填入业务字段，并附带可选 summary。 */
    private static FlowDesignPatch withSummary(Map<String, Object> args,
                                              java.util.function.Consumer<FlowDesignPatch> filler) {
        FlowDesignPatch patch = new FlowDesignPatch();
        filler.accept(patch);
        patch.setSummary(FlowDesignToolSupport.stringArg(args.get("summary")));
        return patch;
    }

    /** 读取必填字符串参数；空则抛参数错误。 */
    private static String requireArg(Map<String, Object> args, String key, String message) {
        String value = FlowDesignToolSupport.stringArg(args.get(key));
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 组装节点：强制 type、必填 data；可选 position。
     * generateIdIfMissing=true 时缺 id 用 n_纳秒，供本会话短名映射。
     */
    @SuppressWarnings("unchecked")
    private static GraphNode buildNode(String type, Map<String, Object> args, boolean generateIdIfMissing) {
        GraphNode node = new GraphNode();
        node.setType(type);
        String id = FlowDesignToolSupport.stringArg(args.get("id"));
        if (id.isEmpty() && generateIdIfMissing) {
            id = "n_" + System.nanoTime();
        }
        node.setId(id);
        Object pos = args.get("position");
        if (pos instanceof Map<?, ?> posMap) {
            GraphNodePosition position = new GraphNodePosition();
            Object x = posMap.get("x");
            Object y = posMap.get("y");
            if (x instanceof Number n) {
                position.setX(n.doubleValue());
            }
            if (y instanceof Number n) {
                position.setY(n.doubleValue());
            }
            node.setPosition(position);
        }
        Object dataObj = args.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            node.setData(new HashMap<>((Map<String, Object>) dataMap));
        } else if (dataObj != null) {
            node.setData(JSON.parseObject(JSON.toJSONString(dataObj), Map.class));
        } else {
            throw new IllegalArgumentException("缺少 data");
        }
        return node;
    }

    /**
     * 组装边：source/target 必填语义由后续校验保证；可选 label。
     * generateIdIfMissing=true 时缺 id 用 e_纳秒。
     */
    private static GraphEdge buildEdge(Map<String, Object> args, boolean generateIdIfMissing) {
        GraphEdge edge = new GraphEdge();
        String id = FlowDesignToolSupport.stringArg(args.get("id"));
        if (id.isEmpty() && generateIdIfMissing) {
            id = "e_" + System.nanoTime();
        }
        edge.setId(id);
        edge.setSource(FlowDesignToolSupport.stringArg(args.get("source")));
        edge.setTarget(FlowDesignToolSupport.stringArg(args.get("target")));
        String label = FlowDesignToolSupport.stringArg(args.get("label"));
        if (!label.isEmpty()) {
            edge.setLabel(label);
        }
        return edge;
    }

    /**
     * 组装运行场景：可选 name、环境、备注、flowSeed。
     * generateIdIfMissing=true 时缺 id 用 sc_纳秒。
     */
    @SuppressWarnings("unchecked")
    private static GraphRunScenario buildScenario(Map<String, Object> args, boolean generateIdIfMissing) {
        GraphRunScenario s = new GraphRunScenario();
        String id = FlowDesignToolSupport.stringArg(args.get("id"));
        if (id.isEmpty() && generateIdIfMissing) {
            id = "sc_" + System.nanoTime();
        }
        s.setId(id);
        String name = FlowDesignToolSupport.stringArg(args.get("name"));
        if (!name.isEmpty()) {
            s.setName(name);
        }
        String envId = FlowDesignToolSupport.stringArg(args.get("testProjectEnvId"));
        if (!envId.isEmpty()) {
            s.setTestProjectEnvId(envId);
        }
        String remark = FlowDesignToolSupport.stringArg(args.get("remark"));
        if (!remark.isEmpty()) {
            s.setRemark(remark);
        }
        Object seed = args.get("flowSeed");
        if (seed instanceof Map<?, ?> seedMap) {
            s.setFlowSeed(new HashMap<>((Map<String, Object>) seedMap));
        }
        return s;
    }
}
