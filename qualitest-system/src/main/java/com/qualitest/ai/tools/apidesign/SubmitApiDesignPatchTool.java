package com.qualitest.ai.tools.apidesign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.apidesign.ApiDesignPatchNormalizer;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatch;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 接收模型提交的设计 patch：规范化校验后写入 SubmitCapture，不写库。
 * <p>
 * 校验失败时返回 errors，提示模型修正后再次 submit。
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
            result.put("hint", replacedPrevious
                    ? "已覆盖本轮先前提交的 patch，已向用户展示修改建议，等待用户确认应用"
                    : "已向用户展示修改建议，等待用户确认应用");
        } else {
            result.put("received", false);
            result.put("error", "校验未通过");
            result.put("hint", "请根据 validation.errors 修正后再次调用 submit_api_design_patch");
        }
        return result.toJSONString();
    }
}
