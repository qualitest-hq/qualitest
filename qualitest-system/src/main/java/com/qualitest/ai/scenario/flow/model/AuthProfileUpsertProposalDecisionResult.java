package com.qualitest.ai.scenario.flow.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 鉴权 Profile 提案确认或拒绝后的回执。
 */
@Getter
@Builder
public class AuthProfileUpsertProposalDecisionResult {

    /** 是否处理成功 */
    private final boolean ok;

    /** 失败时的错误文案列表 */
    private final List<String> errors;

    /** 落盘或提案中的 Profile id */
    private final String profileId;

    /** 操作类型：created / updated */
    private final String action;

    /** 最终状态：confirmed / rejected */
    private final String status;

    /** 相对改前发生变化的字段名 */
    private final List<String> changedFields;

    /** 改后预览快照（确认成功时含落盘后的 id） */
    private final Map<String, Object> after;
}
