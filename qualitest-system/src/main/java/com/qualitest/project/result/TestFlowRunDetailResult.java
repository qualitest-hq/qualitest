package com.qualitest.project.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Run 详情 API 响应：Run 头信息 + 按 step_index 排序的步骤列表。
 * <p>
 * Run 头中的 {@code graphJsonSnapshot} 为触发时固化图，与当前 test_flow 定义可能不同。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestFlowRunDetailResult {

    private TestFlowRunResult run;

    @Builder.Default
    private List<TestFlowRunStepResult> steps = new ArrayList<>();

    /** paused 状态时填充，供恢复决策 UI 使用 */
    private RunPauseInfo pauseInfo;
}
