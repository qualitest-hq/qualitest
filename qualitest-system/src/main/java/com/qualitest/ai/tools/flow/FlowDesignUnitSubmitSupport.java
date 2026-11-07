package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignClientIdMapSupport;
import com.qualitest.ai.scenario.flow.FlowDesignPatchMerger;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.FlowDesignPatchUnitIds;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.tools.FlowDesignPatchStats;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 画布 submit_* 工具的共用提交流程。
 * <p>
 * 提供两条入口：submitUnit 每次只接受一个 Staging 单元（节点、场景、删除等）；
 * submitEdges 专用于边，可一次多条，全部校验通过才写入本轮 Capture，任一条失败则整批不写。
 * 成功后把单元合并进内存工作图，并可选落会话短名映射；本类不直接写测试流业务库。
 */
public class FlowDesignUnitSubmitSupport {

    /** 单单元规范化与校验 */
    private final FlowDesignPatchNormalizer normalizer;
    /** 会话级短名→雪花 id 映射持久化；可为空（不落会话） */
    private final AiChatConversationService aiChatConversationService;
    /** 把已接受单元合并进内存工作图 */
    private final FlowDesignPatchMerger patchMerger;

    /**
     * @param normalizer                 规范化器
     * @param aiChatConversationService  会话服务，可空
     * @param patchMerger                图合并器
     */
    public FlowDesignUnitSubmitSupport(FlowDesignPatchNormalizer normalizer,
                                       AiChatConversationService aiChatConversationService,
                                       FlowDesignPatchMerger patchMerger) {
        this.normalizer = normalizer;
        this.aiChatConversationService = aiChatConversationService;
        this.patchMerger = patchMerger;
    }

    /**
     * 提交恰好一个 Staging 单元。
     * <p>
     * 先统计 patch 内单元数，不是 1 则报错。再规范化校验并写入 Capture（失败只刷新校验摘要，不改已接受累积）。
     * 校验通过则保存短名映射、推进内存工作图，回执 received=true。
     *
     * @param patch    只含一个单元的增量
     * @param ctx      本轮工具上下文（工作图、Capture、短名映射）
     * @param toolName 当前工具名，写入失败 hint 便于模型重试同一工具
     * @return 回执 JSON：validation、unitId、received、hint，以及可选 idMap、patchStats
     */
    public String submitUnit(FlowDesignPatch patch, FlowDesignToolContext ctx, String toolName) {
        if (patch == null) {
            return FlowDesignToolSupport.errorJson("submit 参数为空");
        }
        Set<String> unitIds = FlowDesignPatchUnitIds.enumerate(patch);
        if (unitIds.size() != 1) {
            return FlowDesignToolSupport.errorJson(
                    "每次调用 " + toolName + " 必须恰好产生 1 个 Staging 单元，当前为 "
                            + unitIds.size() + " 个：" + unitIds
                            + "。请只提交一个节点/边/场景变更。");
        }
        String unitId = unitIds.iterator().next();
        Map<String, String> clientIdMap = ctx.getFlowDesignClientIdMap();
        GraphJson baseGraph = ctx.resolveGraphJson();
        FlowDesignPatchNormalizer.NormalizeResult normalized = normalizer.normalizeUnit(
                patch, baseGraph, ctx.getTestProjectId(), clientIdMap);

        boolean hadAccepted = ctx.getSubmitCapture() != null && ctx.getSubmitCapture().hasAccepted();
        if (ctx.getSubmitCapture() != null) {
            ctx.getSubmitCapture().record(normalized);
        }

        JSONObject validation = new JSONObject();
        validation.put("ok", normalized.validation().isOk());
        validation.put("errors", normalized.validation().getErrors());
        validation.put("warnings", normalized.validation().getWarnings());
        JSONObject result = new JSONObject();
        result.put("validation", validation);
        result.put("unitId", unitId);
        result.put("tool", toolName);
        if (hadAccepted) {
            result.put("replacedOrAppended", true);
        }
        Map<String, String> unitIdMap = slimIdMapForUnit(
                clientIdMap, normalized.patch() != null ? normalized.patch() : patch);
        if (!unitIdMap.isEmpty()) {
            result.put("idMap", unitIdMap);
        }
        if (normalized.validation().isOk()) {
            saveClientIdMap(ctx, clientIdMap);
            advanceWorkingGraph(ctx, normalized.patch());
            result.put("received", true);
            result.put("hint", acceptHint(ctx, 1, false));
            result.put("patchStats", FlowDesignPatchStats.build(
                    ctx.getSubmitCapture() != null ? ctx.getSubmitCapture().getNormalizedPatch() : normalized.patch()));
        } else {
            result.put("received", false);
            result.put("error", "校验未通过");
            result.put("hint", "请根据 validation.errors 修正后再次调用 " + toolName);
        }
        return result.toJSONString();
    }

    /**
     * 批量提交边：按条在临时工作图上校验，全部通过后才写入 Capture 并推进正式工作图。
     * <p>
     * 任一条失败则整批不改 Capture / 正式工作图，回执带按边标注的 errors。
     * 入参 patch 应只含 addEdges、updateEdges（及可选 summary）。
     *
     * @param patch    可含多条边的增量
     * @param ctx      本轮工具上下文
     * @param toolName 当前工具名
     * @return 回执 JSON：unitIds、validation、received；成功时含 hint、可选 idMap、patchStats
     */
    public String submitEdges(FlowDesignPatch patch, FlowDesignToolContext ctx, String toolName) {
        if (patch == null) {
            return FlowDesignToolSupport.errorJson("submit 参数为空");
        }
        List<FlowDesignPatch> edgeUnits = splitEdgeUnits(patch);
        if (edgeUnits.isEmpty()) {
            return FlowDesignToolSupport.errorJson("须至少提供一条边");
        }

        Map<String, String> clientIdMap = ctx.getFlowDesignClientIdMap();
        GraphJson working = FlowDesignPatchMerger.cloneGraph(ctx.resolveGraphJson());
        List<FlowDesignPatchNormalizer.NormalizeResult> accepted = new ArrayList<>(edgeUnits.size());
        List<String> allErrors = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        List<String> unitIdList = new ArrayList<>(edgeUnits.size());

        for (int i = 0; i < edgeUnits.size(); i++) {
            FlowDesignPatch unit = edgeUnits.get(i);
            String unitId = FlowDesignPatchUnitIds.enumerate(unit).stream()
                    .findFirst().orElse("edge#" + (i + 1));
            unitIdList.add(unitId);
            FlowDesignPatchNormalizer.NormalizeResult normalized = normalizer.normalizeUnit(
                    unit, working, ctx.getTestProjectId(), clientIdMap);
            if (normalized == null) {
                allErrors.add("[" + unitId + "] 规范化返回空");
                continue;
            }
            DesignValidationResult validation = normalized.validation();
            if (validation != null && validation.getWarnings() != null) {
                for (String w : validation.getWarnings()) {
                    allWarnings.add("[" + unitId + "] " + w);
                }
            }
            if (validation == null || !validation.isOk()) {
                List<String> errs = validation != null && validation.getErrors() != null
                        ? validation.getErrors()
                        : List.of("校验失败");
                for (String err : errs) {
                    allErrors.add("[" + unitId + "] " + err);
                }
                continue;
            }
            accepted.add(normalized);
            // 仅在临时工作图上推进，使后续边可引用本批已通过的端点
            if (normalized.patch() != null && patchMerger != null) {
                working = patchMerger.mergeAll(working, normalized.patch(), new ArrayList<>());
            }
        }

        JSONObject result = new JSONObject();
        result.put("tool", toolName);
        result.put("unitIds", unitIdList);
        if (unitIdList.size() == 1) {
            result.put("unitId", unitIdList.get(0));
        }
        JSONObject validationJson = new JSONObject();
        boolean ok = allErrors.isEmpty();
        validationJson.put("ok", ok);
        validationJson.put("errors", allErrors);
        validationJson.put("warnings", allWarnings);
        result.put("validation", validationJson);

        if (!ok) {
            result.put("received", false);
            result.put("error", "校验未通过");
            result.put("hint", "请根据 validation.errors 修正后再次调用 " + toolName
                    + "（本批未写入，可整批重试）");
            return result.toJSONString();
        }

        boolean hadAccepted = ctx.getSubmitCapture() != null && ctx.getSubmitCapture().hasAccepted();
        Set<String> batchEntityIds = new LinkedHashSet<>();
        for (FlowDesignPatchNormalizer.NormalizeResult nr : accepted) {
            if (ctx.getSubmitCapture() != null) {
                ctx.getSubmitCapture().record(nr);
            }
            advanceWorkingGraph(ctx, nr.patch());
            if (nr.patch() != null) {
                batchEntityIds.addAll(FlowDesignPatchUnitIds.collectEntityIds(nr.patch()));
            }
        }
        if (hadAccepted) {
            result.put("replacedOrAppended", true);
        }
        saveClientIdMap(ctx, clientIdMap);
        Map<String, String> idMap = slimIdMapForEntities(clientIdMap, batchEntityIds);
        if (!idMap.isEmpty()) {
            result.put("idMap", idMap);
        }
        result.put("received", true);
        result.put("hint", acceptHint(ctx, unitIdList.size(), true));
        result.put("patchStats", FlowDesignPatchStats.build(
                ctx.getSubmitCapture() != null ? ctx.getSubmitCapture().getNormalizedPatch() : null));
        return result.toJSONString();
    }

    /**
     * 把 patch 中的新增边、修改边拆成「每条边一个 Staging 单元」的列表，保持原顺序。
     *
     * @param patch 可含多条边的增量；为 null 时返回空列表
     * @return 单边 patch 列表
     */
    static List<FlowDesignPatch> splitEdgeUnits(FlowDesignPatch patch) {
        List<FlowDesignPatch> units = new ArrayList<>();
        if (patch == null) {
            return units;
        }
        String summary = patch.getSummary();
        appendEdgeUnits(units, patch.getAddEdges(), summary, true);
        appendEdgeUnits(units, patch.getUpdateEdges(), summary, false);
        return units;
    }

    /**
     * 把边列表逐条包成单边 patch 追加到 units。
     *
     * @param units   输出列表
     * @param edges   边列表，可空
     * @param summary 复制到每个单边 patch 的说明
     * @param add     true 写入 addEdges，false 写入 updateEdges
     */
    private static void appendEdgeUnits(List<FlowDesignPatch> units, List<GraphEdge> edges,
                                        String summary, boolean add) {
        if (edges == null) {
            return;
        }
        for (GraphEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            FlowDesignPatch u = new FlowDesignPatch();
            if (add) {
                u.getAddEdges().add(edge);
            } else {
                u.getUpdateEdges().add(edge);
            }
            u.setSummary(summary);
            units.add(u);
        }
    }

    /**
     * 若上下文带 AI 会话 id，把当前短名映射写入会话。
     *
     * @param ctx         工具上下文
     * @param clientIdMap 短名→雪花映射，可空则跳过
     */
    private void saveClientIdMap(FlowDesignToolContext ctx, Map<String, String> clientIdMap) {
        if (aiChatConversationService != null
                && ctx.getAiChatSessionId() != null
                && clientIdMap != null) {
            aiChatConversationService.saveFlowDesignClientIdMap(ctx.getAiChatSessionId(), clientIdMap);
        }
    }

    /**
     * 组装校验通过后的提示文案。
     * 全自动强调内存累积与跑流时落盘；半自动强调进入本轮累积、待面板确认。
     *
     * @param ctx   工具上下文（读是否全自动）
     * @param count 本次接受的单元或边条数
     * @param edges true 表示文案按「边」计数，false 按「单元」
     * @return 提示字符串
     */
    private static String acceptHint(FlowDesignToolContext ctx, int count, boolean edges) {
        if (ctx.isAutopilotEnabled()) {
            return count == 1
                    ? "单元已接受（内存累积）；调用 run_test_flow 时会自动落盘，无需单独落盘步骤"
                    : "已接受 " + count + (edges ? " 条边" : " 个单元")
                    + "（内存累积）；调用 run_test_flow 时会自动落盘";
        }
        return count == 1
                ? "单元已接受并进入本轮累积 patch，等待用户在面板确认；可继续调用其它 submit_* 或结束并总结"
                : "已接受 " + count + (edges ? " 条边" : " 个单元")
                + "进入本轮累积 patch，等待用户在面板确认";
    }

    /**
     * 从会话短名映射中筛出 value 落在给定实体 id 集合内的条目，避免整表回传占满上下文。
     *
     * @param clientIdMap 完整短名映射
     * @param entityIds   本批相关的节点/边/场景实体 id
     * @return 精简后的映射快照；无匹配时为空 Map
     */
    static Map<String, String> slimIdMapForEntities(Map<String, String> clientIdMap, Set<String> entityIds) {
        if (clientIdMap == null || clientIdMap.isEmpty() || entityIds == null || entityIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> slim = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : clientIdMap.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (entityIds.contains(e.getValue().trim())) {
                slim.put(e.getKey(), e.getValue());
            }
        }
        return FlowDesignClientIdMapSupport.snapshot(slim);
    }

    /**
     * 按单个 patch 内的实体 id 筛短名映射，供单单元回执使用。
     *
     * @param clientIdMap 完整短名映射
     * @param unitPatch   本单元规范化后的 patch
     * @return 精简映射
     */
    static Map<String, String> slimIdMapForUnit(Map<String, String> clientIdMap, FlowDesignPatch unitPatch) {
        return slimIdMapForEntities(clientIdMap, FlowDesignPatchUnitIds.collectEntityIds(unitPatch));
    }

    /**
     * 把刚接受的单元合并进上下文内存工作图（不写业务库），
     * 使本轮后续只读查图工具能看到已接受变更。
     *
     * @param ctx       工具上下文
     * @param unitPatch 已通过校验的单单元 patch
     */
    private void advanceWorkingGraph(FlowDesignToolContext ctx, FlowDesignPatch unitPatch) {
        if (ctx == null || unitPatch == null || patchMerger == null) {
            return;
        }
        GraphJson base = ctx.resolveGraphJson();
        if (base == null) {
            return;
        }
        ctx.advanceWorkingGraph(patchMerger.mergeAll(base, unitPatch, new ArrayList<>()));
    }
}
