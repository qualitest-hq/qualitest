package com.qualitest.project.service;

import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.TestFlowRunDetailResult;

/**
 * 测试流 Run 执行与报告查询。
 */
public interface ITestFlowExecutionService {

    /**
     * 触发一次 Run 并在当前请求内同步跑完。
     *
     * @return 新建的 testFlowRunId
     */
    Long triggerRun(TriggerTestFlowRunParams params);

    /**
     * 查询 Run 头、步骤列表及触发时的 graph_json_snapshot。
     */
    TestFlowRunDetailResult getRunDetail(Long testFlowRunId);

    /**
     * 恢复 paused 状态的 Run。
     */
    ResumeRunResult resumeRun(Long testFlowRunId, ResumeTestFlowRunParams params);
}
