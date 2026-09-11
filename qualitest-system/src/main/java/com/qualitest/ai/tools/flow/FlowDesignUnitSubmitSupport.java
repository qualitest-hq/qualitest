package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignClientIdMapSupport;
import com.qualitest.ai.scenario.flow.FlowDesignPatchMerger;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.FlowDesignPatchUnitIds;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.tools.FlowDesignPatchStats;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.flow.model.GraphJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 各 submit_* 工具共用的单单元提交流程。
 * <p>
 * 职责：统计 Staging 单元数（必须恰好 1 个）→ 单单元规范化校验 → 写入本轮 Capture →
 * 成功则持久化短名映射、把该单元合并进内存工作图，供后续只读查图工具看到本轮已接受变更。
 * 失败不改已接受累积 patch；模型可按 errors 修正后再次调用同一工具。
 */
public class FlowDesignUnitSubmitSupport {

    private final FlowDesignPatchNormalizer normalizer;
    private final AiChatConversationService aiChatConversationService;
    private final FlowDesignPatchMerger patchMerger;

    public FlowDesignUnitSubmitSupport(FlowDesignPatchNormalizer normalizer,
                                       AiChatConversationService aiChatConversationService,
                                       FlowDesignPatchMerger patchMerger) {
        this.normalizer = normalizer;
        this.aiChatConversationService = aiChatConversationService;
        this.patchMerger = patchMerger;
    }

    /**
     * 提交恰好一个 Staging 单元。
     *
     * @param patch    只含一个单元的增量（加节点 / 改节点 / 加边 / …）
     * @param ctx      本轮工具上下文（工作图、Capture、短名映射）
     * @param toolName 当前工具名，写入失败提示便于模型重试
     * @return 给模型看的 JSON：validation、unitId、received、hint、patchStats 等
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
        // 基准图优先用本轮已接受单元合并后的工作图
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
            // 本轮此前已有成功单元：本次可能是追加，也可能是同 unitId 覆盖重试
            result.put("replacedOrAppended", true);
        }
        if (clientIdMap != null && !clientIdMap.isEmpty()) {
            result.put("idMap", FlowDesignClientIdMapSupport.snapshot(clientIdMap));
        }
        if (normalized.validation().isOk()) {
            if (aiChatConversationService != null
                    && ctx.getAiChatSessionId() != null
                    && clientIdMap != null) {
                aiChatConversationService.saveFlowDesignClientIdMap(ctx.getAiChatSessionId(), clientIdMap);
            }
            advanceWorkingGraph(ctx, normalized.patch());
            result.put("received", true);
            result.put("hint", "单元已接受并进入本轮累积 patch，等待用户在面板确认；可继续调用其它 submit_* 或结束并总结");
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
     * 把刚接受的单元合并进内存工作图（不写业务库）。
     * 之后 get_node_detail / get_edge_detail / get_graph_summary 等会读到含本单元的图。
     */
    private void advanceWorkingGraph(FlowDesignToolContext ctx, FlowDesignPatch unitPatch) {
        if (ctx == null || unitPatch == null || patchMerger == null) {
            return;
        }
        GraphJson base = ctx.resolveGraphJson();
        if (base == null) {
            return;
        }
        List<String> warnings = new ArrayList<>();
        GraphJson next = patchMerger.mergeAll(base, unitPatch, warnings);
        ctx.advanceWorkingGraph(next);
    }
}
