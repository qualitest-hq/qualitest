package com.qualitest.ai.tools.apidesign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.apidesign.ApiDesignPatchNormalizer;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatch;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 接收模型提交的 API 设计增量 patch：规范化校验后写入 SubmitCapture，本工具不写接口库。
 * <p>
 * 半自动：回执提示等待用户在 Diff 区确认应用。
 * 全自动：回执提示前端将自动合并进工作台草稿，勿催用户勾选；保存接口库仍须用户在设计页操作。
 * 同轮多次 submit 后者覆盖前者（replacedPrevious=true）。校验失败返回 errors，模型应修正后重调。
 */
@RequiredArgsConstructor
public class SubmitApiDesignPatchTool implements ApiDesignTool {

    private final ApiDesignPatchNormalizer normalizer;

    @Override
    public String getName() {
        return ApiDesignToolNames.SUBMIT_API_DESIGN_PATCH.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, ApiDesignToolContext ctx) {
        ApiDesignPatch patch;
        try {
            patch = JSON.parseObject(JSON.toJSONString(arguments), ApiDesignPatch.class);
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("无法解析 submit 参数: " + e.getMessage());
        }
        if (patch == null) {
            return FlowDesignToolSupport.errorJson("submit 参数为空");
        }
        boolean replacedPrevious = ctx.getSubmitCapture() != null && ctx.getSubmitCapture().isSubmitted();
        ApiDesignPatchNormalizer.NormalizeResult normalized = normalizer.normalize(patch);
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
            boolean autopilot = ctx != null && ctx.isAutopilotEnabled();
            if (autopilot) {
                result.put("hint", replacedPrevious
                        ? "已覆盖本轮先前 patch；全自动将自动应用到工作台草稿，勿催用户勾选（仍须人手保存接口库）"
                        : "全自动将自动应用到工作台草稿，勿催用户勾选（仍须人手保存接口库）");
            } else {
                result.put("hint", replacedPrevious
                        ? "已覆盖本轮先前提交的 patch，已向用户展示修改建议，等待用户确认应用"
                        : "已向用户展示修改建议，等待用户确认应用");
            }
        } else {
            result.put("received", false);
            result.put("error", "校验未通过");
            result.put("hint", "请根据 validation.errors 修正后再次调用 submit_api_design_patch");
        }
        return result.toJSONString();
    }
}
