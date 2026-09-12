package com.qualitest.ai.scenario.flow.model;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行风险预检结果（不写库）。
 * <p>
 * 聚合：鉴权凭证缺失、登录口未抽取、HTTP 必填测值缺失等错误文案。
 * 不用于阻断保存；用于画布提示，以及正式开跑前的拦截依据。
 */
@Getter
@Builder
public class FlowDesignSavePrecheckResult {

    /** true 表示无预检错误 */
    private final boolean ok;

    /**
     * 预检错误列表。
     * 每条通常以稳定 CODE 开头（如 AUTH_TOKEN_MISSING: …），便于前端按码展示。
     */
    @Builder.Default
    private final List<String> errors = new ArrayList<>();
}
