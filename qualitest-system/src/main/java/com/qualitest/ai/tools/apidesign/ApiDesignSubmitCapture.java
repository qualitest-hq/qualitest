package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.scenario.apidesign.ApiDesignPatchNormalizer;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatch;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignValidationResult;
import lombok.Getter;

/**
 * 单轮对话内 submit_api_design_patch 的结果暂存。
 * <p>
 * Agent 在模型跑完后读取：是否提交过、规范化后的 patch、校验结论。
 * 多次 submit 时后者覆盖前者。
 */
@Getter
public class ApiDesignSubmitCapture {

    /** 本轮是否至少调用过一次 submit */
    private boolean submitted;

    /** 最近一次规范化后的 patch（校验失败时也可能非空但 validation.ok=false） */
    private ApiDesignPatch normalizedPatch;

    /** 最近一次校验结论 */
    private ApiDesignValidationResult validation;

    /** 记录一次 submit 的规范化结果 */
    public void record(ApiDesignPatchNormalizer.NormalizeResult result) {
        this.submitted = true;
        this.normalizedPatch = result.patch();
        this.validation = result.validation();
    }
}
