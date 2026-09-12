package com.qualitest.ai.scenario.flow.model;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存前预检结果。
 * <p>
 * 聚合鉴权凭证缺失、登录未抽取、HTTP 必填测值缺失等错误文案；
 * 不写库。前端在真正调用保存 API 前展示，减少「Staging 已全确认、保存才失败」的情况。
 */
@Getter
@Builder
public class FlowDesignSavePrecheckResult {

    /** true 表示无预检错误，可继续保存 */
    private final boolean ok;

    /**
     * 预检错误列表。
     * 每条通常以稳定 CODE 开头（如 AUTH_TOKEN_MISSING: …），供前端识别展示。
     */
    @Builder.Default
    private final List<String> errors = new ArrayList<>();
}
