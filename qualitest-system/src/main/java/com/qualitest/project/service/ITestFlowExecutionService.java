package com.qualitest.project.service;

import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;

/**
 * 测试流 Run 执行与报告查询。
 */
public interface ITestFlowExecutionService {

    /**
     * 触发一次 Run。
     * <p>
     * 成员与图就绪校验通过后，写入 status=running 的运行记录，并立刻返回 runId；
     * 图遍历在后台线程继续。每完成一个可展示步骤即写入步骤表，执行中可查详情看进度。
     * 校验失败时不建记录、直接抛错。
     *
     * @return 新建的 testFlowRunId（返回时通常仍为 running）
     */
    Long triggerRun(TriggerTestFlowRunParams params);

    /**
     * 查询 Run 头信息、已落库步骤列表，以及触发时固化的 graph_json_snapshot。
     * 执行中也可调用，步骤列表随执行推进变长。
     */
    TestFlowRunDetailResult getRunDetail(Long testFlowRunId);

    /**
     * 阻塞轮询，直到 Run 离开 running（passed / failed / paused 等），或超过 timeoutMs。
     * 超时仍返回当前快照（可能仍是 running）。
     *
     * @param timeoutMs 最长等待毫秒数
     */
    TestFlowRunResult awaitRunTerminal(Long testFlowRunId, long timeoutMs);

    /**
     * 恢复 paused 状态的 Run（续跑或按决策跳过/重试等）。
     */
    ResumeRunResult resumeRun(Long testFlowRunId, ResumeTestFlowRunParams params);
}
