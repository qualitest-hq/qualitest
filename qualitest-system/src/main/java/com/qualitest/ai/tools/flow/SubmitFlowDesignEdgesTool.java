package com.qualitest.ai.tools.flow;

import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.model.GraphEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 测试流画布「批量提交边」工具（工具名 submit_edges）。
 * <p>
 * 一次调用可新增或修改多条边：参数可为 edges 数组，和/或 chainNodeIds 按节点顺序串成相邻边。
 * 先把参数拼成只含边的增量 patch，再交给提交流程做整批校验与本轮累积；不在本类内写业务库。
 */
public class SubmitFlowDesignEdgesTool implements QualitestTool {

    /** 共用提交流程：校验、写入本轮 Capture、推进内存工作图 */
    private final FlowDesignUnitSubmitSupport support;

    /**
     * @param support 提交流程依赖，不可为空
     */
    public SubmitFlowDesignEdgesTool(FlowDesignUnitSubmitSupport support) {
        this.support = support;
    }

    /** @return 固定为 submit_edges */
    @Override
    public String getName() {
        return FlowDesignToolNames.SUBMIT_EDGES.getId();
    }

    /**
     * 解析工具参数为多边 patch，再整批提交。
     * 参数非法时立即返回 error JSON，不写入 Capture。
     *
     * @param arguments 工具入参（edges / chainNodeIds / summary 等）
     * @param ctx       本轮工具上下文
     * @return 提交回执 JSON
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        FlowDesignPatch patch;
        try {
            patch = upsertEdges(arguments != null ? arguments : Map.of());
        } catch (IllegalArgumentException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("无法解析参数: " + e.getMessage());
        }
        if (patch == null) {
            return FlowDesignToolSupport.errorJson("submit 参数为空");
        }
        return support.submitEdges(patch, ctx, getName());
    }

    /**
     * 把工具参数组装成可含多条边的增量 patch。
     * <p>
     * 处理顺序：读取 edges 数组 → 用 chainNodeIds 生成相邻 add 边（与 edges 中已有
     * 相同 source→target 的项去重）→ 为缺 id 的 add 边补临时短名 → 逐条校验 op 并合并进同一 patch。
     * edges 与 chainNodeIds 至少提供一种，且最终至少产生一条边，否则抛 IllegalArgumentException。
     *
     * @param args 原始工具参数
     * @return 只含 addEdges / updateEdges（及可选 summary）的 patch
     */
    @SuppressWarnings("unchecked")
    static FlowDesignPatch upsertEdges(Map<String, Object> args) {
        List<Map<String, Object>> edgeArgsList = new ArrayList<>();
        Object edgesObj = args.get("edges");
        if (edgesObj instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item == null) {
                    continue;
                }
                if (!(item instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("edges[" + i + "] 须为对象");
                }
                edgeArgsList.add(new HashMap<>((Map<String, Object>) item));
            }
        } else if (edgesObj != null) {
            throw new IllegalArgumentException("edges 须为数组");
        }

        Set<String> seenPairs = new HashSet<>();
        for (Map<String, Object> edgeArgs : edgeArgsList) {
            String source = FlowDesignToolSupport.stringArg(edgeArgs.get("source"));
            String target = FlowDesignToolSupport.stringArg(edgeArgs.get("target"));
            if (!source.isEmpty() && !target.isEmpty()) {
                seenPairs.add(pairKey(source, target));
            }
        }

        List<Map<String, Object>> all = new ArrayList<>();
        Object chainObj = args.get("chainNodeIds");
        if (chainObj != null) {
            if (!(chainObj instanceof List<?> chainList)) {
                throw new IllegalArgumentException("chainNodeIds 须为字符串数组");
            }
            if (chainList.size() < 2) {
                throw new IllegalArgumentException("chainNodeIds 至少需要 2 个节点 id 才能串链");
            }
            for (int i = 0; i < chainList.size() - 1; i++) {
                String source = chainList.get(i) == null ? "" : String.valueOf(chainList.get(i)).trim();
                String target = chainList.get(i + 1) == null ? "" : String.valueOf(chainList.get(i + 1)).trim();
                if (source.isEmpty() || target.isEmpty()) {
                    throw new IllegalArgumentException("chainNodeIds[" + i + "] 与下一元素均须为非空节点 id");
                }
                if (seenPairs.contains(pairKey(source, target))) {
                    continue;
                }
                Map<String, Object> edgeArgs = new HashMap<>();
                edgeArgs.put("op", "add");
                edgeArgs.put("source", source);
                edgeArgs.put("target", target);
                edgeArgs.put("id", "e_" + System.nanoTime() + "_" + i);
                all.add(edgeArgs);
                seenPairs.add(pairKey(source, target));
            }
        }

        for (int i = 0; i < edgeArgsList.size(); i++) {
            Map<String, Object> edgeArgs = edgeArgsList.get(i);
            String op = FlowDesignToolSupport.stringArg(edgeArgs.get("op")).trim().toLowerCase(Locale.ROOT);
            if ("add".equals(op) && FlowDesignToolSupport.stringArg(edgeArgs.get("id")).isEmpty()) {
                edgeArgs.put("id", "e_" + System.nanoTime() + "_x" + i);
            }
            all.add(edgeArgs);
        }

        if (all.isEmpty()) {
            throw new IllegalArgumentException("须提供 edges 数组和/或 chainNodeIds（至少产生一条边）");
        }

        FlowDesignPatch merged = new FlowDesignPatch();
        String topSummary = FlowDesignToolSupport.stringArg(args.get("summary"));
        if (!topSummary.isEmpty()) {
            merged.setSummary(topSummary);
        }
        for (int i = 0; i < all.size(); i++) {
            FlowDesignPatch unit;
            try {
                unit = upsertOneEdge(all.get(i));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("edges 项 #" + (i + 1) + ": " + e.getMessage());
            }
            merged.getAddEdges().addAll(unit.getAddEdges());
            merged.getUpdateEdges().addAll(unit.getUpdateEdges());
            if ((merged.getSummary() == null || merged.getSummary().isBlank())
                    && unit.getSummary() != null && !unit.getSummary().isBlank()) {
                merged.setSummary(unit.getSummary());
            }
        }
        return merged;
    }

    /**
     * 按单条边参数组装只含一条边的 patch。
     * op=add 须 source、target；op=update 须 id。
     *
     * @param args 单条边字段（op / id / source / target / label / summary）
     * @return 单边 patch
     */
    private static FlowDesignPatch upsertOneEdge(Map<String, Object> args) {
        String op = FlowDesignToolSupport.stringArg(args.get("op")).trim().toLowerCase(Locale.ROOT);
        if (!"add".equals(op) && !"update".equals(op)) {
            throw new IllegalArgumentException("缺少或非法 op（须为 add 或 update）");
        }
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSummary(FlowDesignToolSupport.stringArg(args.get("summary")));
        if ("add".equals(op)) {
            String source = FlowDesignToolSupport.stringArg(args.get("source"));
            String target = FlowDesignToolSupport.stringArg(args.get("target"));
            if (source.isEmpty() || target.isEmpty()) {
                throw new IllegalArgumentException("op=add 须提供 source 与 target");
            }
            patch.getAddEdges().add(buildEdge(args, true));
        } else {
            if (FlowDesignToolSupport.stringArg(args.get("id")).isEmpty()) {
                throw new IllegalArgumentException("op=update 须提供边 id");
            }
            patch.getUpdateEdges().add(buildEdge(args, false));
        }
        return patch;
    }

    /**
     * 组装边对象：写入 id、source、target、可选 label。
     * generateIdIfMissing 为 true 且缺 id 时生成 e_ 前缀临时短名。
     *
     * @param args                 边字段
     * @param generateIdIfMissing  缺 id 时是否自动生成
     * @return 边模型
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
     * 端点对去重键：source 与 target 用不可见分隔符拼接。
     *
     * @param source 起点节点 id
     * @param target 终点节点 id
     * @return 去重用字符串键
     */
    private static String pairKey(String source, String target) {
        return source + "\0" + target;
    }
}
