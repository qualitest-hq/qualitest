package com.qualitest.ai.scenario.flow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 「按项目鉴权刷新本流托管头」预览结果：产出 Staging 可用的 patch，不写 test_flow。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshAuthHeadersResult {

    /** 仅含变更节点的 updateNodes；无变更时为空 patch */
    private FlowDesignPatch patch;

    /** 稳定码 warnings（如 AUTH_HEADER_MANAGED） */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** 发生托管头变更的节点数 */
    private int changedCount;

    /** 人类可读摘要（无变更时说明原因） */
    private String message;
}
