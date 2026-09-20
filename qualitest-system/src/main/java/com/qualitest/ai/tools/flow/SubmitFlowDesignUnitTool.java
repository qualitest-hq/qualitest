package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Web 造流助手的画布单元提交工具。
 * <p>
 * 模型每次调用只改一类对象：某型节点 upsert、边 upsert、场景 upsert、或统一删除。
 * 把工具参数拼成只含一个 Staging 单元的增量 patch，再交给共用提交流程做校验与本轮累积。
 * 不直接写业务库；成功单元进入本轮累积 patch，用户在面板确认后才持久化。
 */
public class SubmitFlowDesignUnitTool implements QualitestTool {

    /** 工具名，例如 submit_http_node */
    private final String name;
    /** 将本次 arguments 转成单单元 patch；参数非法时抛 IllegalArgumentException */
    private final Function<Map<String, Object>, FlowDesignPatch> patchBuilder;
    /** 单单元规范化、Capture 写入、推进内存工作图 */
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
     * 参数不合法时立刻返回 error JSON，不写入 Capture。
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
     * 按 op 新增或修改指定类型节点。
     * type 在注册时写死（http/assert/…），不信任参数里的类型字段。
     * op=update 必须带 id；op=add 缺 id 时生成临时短名 n_*。
     */
    public static FlowDesignPatch upsertNode(String type, Map<String, Object> args) {
        String op = requireOp(args);
        if ("add".equals(op)) {
            return addNode(type, args);
        }
        return updateNode(type, args);
    }

    /**
     * 按 op 新增或修改边。
     * op=add 必须带 source、target；op=update 必须带 id。
     */
    public static FlowDesignPatch upsertEdge(Map<String, Object> args) {
        String op = requireOp(args);
        if ("add".equals(op)) {
            return addEdge(args);
        }
        return updateEdge(args);
    }

    /**
     * 按 op 新增或修改运行场景。
     * op=update 必须带 id；不会改写画布默认运行场景。
     */
    public static FlowDesignPatch upsertScenario(Map<String, Object> args) {
        String op = requireOp(args);
        if ("add".equals(op)) {
            return addScenario(args);
        }
        return updateScenario(args);
    }

    /**
     * 统一删除：kind 为 node、edge 或 scenario，id 为待删对象标识。
     * 节点/边写入 suggestedDeletes；场景写入 scenarioPatch.deleteScenarioIds。
     */
    public static FlowDesignPatch delete(Map<String, Object> args) {
        String kind = requireArg(args, "kind", "缺少 kind（node|edge|scenario）")
                .trim().toLowerCase(Locale.ROOT);
        String id = requireArg(args, "id", "缺少 id");
        return switch (kind) {
            case "node" -> withSummary(args, patch -> patch.getSuggestedDeletes().getNodeIds().add(id));
            case "edge" -> withSummary(args, patch -> patch.getSuggestedDeletes().getEdgeIds().add(id));
            case "scenario" -> withSummary(args, patch -> {
                FlowDesignScenarioPatch sp = new FlowDesignScenarioPatch();
                sp.setDeleteScenarioIds(List.of(id));
                patch.setScenarioPatch(sp);
            });
            default -> throw new IllegalArgumentException("kind 须为 node、edge 或 scenario");
        };
    }

    /** 组装 addNodes 单元素 patch */
    private static FlowDesignPatch addNode(String type, Map<String, Object> args) {
        return withSummary(args, patch -> patch.getAddNodes().add(buildNode(type, args, true)));
    }

    /** 组装 updateNodes 单元素 patch；缺 id 则抛错 */
    private static FlowDesignPatch updateNode(String type, Map<String, Object> args) {
        requireArg(args, "id", "op=update 须提供节点 id");
        return withSummary(args, patch -> patch.getUpdateNodes().add(buildNode(type, args, false)));
    }

    /** 组装 addEdges 单元素 patch；缺 source/target 则抛错；缺 id 时生成 e_* */
    private static FlowDesignPatch addEdge(Map<String, Object> args) {
        String source = FlowDesignToolSupport.stringArg(args.get("source"));
        String target = FlowDesignToolSupport.stringArg(args.get("target"));
        if (source.isEmpty() || target.isEmpty()) {
            throw new IllegalArgumentException("op=add 须提供 source 与 target");
        }
        return withSummary(args, patch -> patch.getAddEdges().add(buildEdge(args, true)));
    }

    /** 组装 updateEdges 单元素 patch；缺 id 则抛错 */
    private static FlowDesignPatch updateEdge(Map<String, Object> args) {
        requireArg(args, "id", "op=update 须提供边 id");
        return withSummary(args, patch -> patch.getUpdateEdges().add(buildEdge(args, false)));
    }

    /** 组装 addScenarios 单元素 patch；缺 id 时生成 sc_* */
    private static FlowDesignPatch addScenario(Map<String, Object> args) {
        return withSummary(args, patch -> {
            FlowDesignScenarioPatch sp = new FlowDesignScenarioPatch();
            sp.getAddScenarios().add(buildScenario(args, true));
            patch.setScenarioPatch(sp);
        });
    }

    /** 组装 updateScenarios 单元素 patch；缺 id 则抛错 */
    private static FlowDesignPatch updateScenario(Map<String, Object> args) {
        requireArg(args, "id", "op=update 须提供 scenario id");
        return withSummary(args, patch -> {
            FlowDesignScenarioPatch sp = new FlowDesignScenarioPatch();
            sp.getUpdateScenarios().add(buildScenario(args, false));
            patch.setScenarioPatch(sp);
        });
    }

    /** 读取并校验 op，合法值仅 add、update（大小写不敏感） */
    private static String requireOp(Map<String, Object> args) {
        String op = FlowDesignToolSupport.stringArg(args.get("op")).trim().toLowerCase(Locale.ROOT);
        if (!"add".equals(op) && !"update".equals(op)) {
            throw new IllegalArgumentException("缺少或非法 op（须为 add 或 update）");
        }
        return op;
    }

    /** 新建空 patch，执行 filler 填业务字段，再附上可选 summary */
    private static FlowDesignPatch withSummary(Map<String, Object> args,
                                              java.util.function.Consumer<FlowDesignPatch> filler) {
        FlowDesignPatch patch = new FlowDesignPatch();
        filler.accept(patch);
        patch.setSummary(FlowDesignToolSupport.stringArg(args.get("summary")));
        return patch;
    }

    /** 读取必填字符串参数；空则抛参数错误 */
    private static String requireArg(Map<String, Object> args, String key, String message) {
        String value = FlowDesignToolSupport.stringArg(args.get(key));
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 组装节点：强制写入 type，data 必填；不从参数读取 position（坐标由规范化阶段自动排版写入）。
     * generateIdIfMissing 为 true 且缺 id 时用 n_纳秒作临时短名，供会话内映射为雪花。
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
     * 组装边：写入 id、source、target、可选 label。
     * generateIdIfMissing 为 true 且缺 id 时用 e_纳秒作临时短名。
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
     * 组装运行场景：可选 name、环境 id、备注、flowSeed。
     * generateIdIfMissing 为 true 且缺 id 时用 sc_纳秒作临时短名。
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
