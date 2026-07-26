package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignPatchStats;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * submit_flow_design_patch：接收 patch、规范化校验，不写库。
 * 结果写入 {@link FlowDesignToolContext#getSubmitCapture()}（若已注入）。
 */
@RequiredArgsConstructor
public class SubmitFlowDesignPatchTool implements QualitestTool {

    private final FlowDesignPatchNormalizer normalizer;

    @Override
    public String getName() {
        return FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        FlowDesignPatch patch;
        try {
            patch = JSON.parseObject(JSON.toJSONString(arguments), FlowDesignPatch.class);
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("无法解析 submit 参数: " + e.getMessage());
        }
        if (patch == null) {
            return FlowDesignToolSupport.errorJson("submit 参数为空");
        }
        boolean replacedPrevious = ctx.getSubmitCapture() != null && ctx.getSubmitCapture().isSubmitted();
        FlowDesignPatchNormalizer.NormalizeResult normalized = normalizer.normalize(
                patch, ctx.getGraphJson(), ctx.getTestProjectId());
        if (ctx.getSubmitCapture() != null) {
            ctx.getSubmitCapture().record(normalized);
        }
        JSONObject validation = new JSONObject();
        validation.put("ok", normalized.validation().isOk());
        validation.put("errors", normalized.validation().getErrors());
        validation.put("warnings", normalized.validation().getWarnings());
        JSONObject result = new JSONObject();
        result.put("validation", validation);
        if (replacedPrevious) {
            result.put("replacedPrevious", true);
        }
        if (normalized.validation().isOk()) {
            result.put("received", true);
            result.put("hint", replacedPrevious
                    ? "已覆盖本轮先前提交的 patch，已向用户展示修改建议，等待用户在面板确认合并"
                    : "已向用户展示修改建议，等待用户在面板确认合并");
            result.put("patchStats", FlowDesignPatchStats.build(normalized.patch()));
        } else {
            result.put("received", false);
            result.put("error", "校验未通过");
            result.put("hint", replacedPrevious
                    ? "已覆盖本轮先前 patch，但校验未通过；请根据 validation.errors 修正后再次调用 submit_flow_design_patch"
                    : "请根据 validation.errors 修正后再次调用 submit_flow_design_patch");
        }
        return result.toJSONString();
    }
}
