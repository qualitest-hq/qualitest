package com.qualitest.ai.scenario.flow.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户确认或拒绝素材库写入提案后的响应体。
 */
@Getter
@Builder
public class AssetUpsertProposalDecisionResult {

    /** true 表示处理成功；false 时看 errors */
    private final boolean ok;

    /** 失败原因列表；成功时为空列表 */
    private final List<String> errors;

    /** 处理的素材 key */
    private final String key;

    /** 提案上的动作标记：created 或 updated */
    private final String action;

    /** 处理后的状态：confirmed 或 rejected */
    private final String status;

    /** 字段名列表（不含明文值） */
    private final List<String> fields;

    /** 占位符提示，例如 {{asset.clientAuth.<field>}} */
    private final String placeholderHint;
}
