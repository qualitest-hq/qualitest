package com.qualitest.project.runhub;

import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 环境跑流槽生命周期。
 * <p>
 * 负责两件事：Run 进入终态后释放该环境的占用槽并清理运行中心活动项；
 * 槽空出后从该环境排队弹出队首，再次触发开跑，并把新 runId 回写给等待方。
 * 与 Run 状态落库、触发编排拆开，避免互相循环依赖。
 */
@Component
public class EnvRunSlotLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EnvRunSlotLifecycle.class);

    /** 环境锁、排队、活动索引 */
    private final EnvRunConcurrencyService concurrencyService;
    /** 查 Run 取环境 id */
    private final ITestFlowRunService testFlowRunService;
    /** 由流反查项目 id（调用方未传入时） */
    private final ITestFlowService testFlowService;
    /** 提升排队项时再次触发跑流（懒加载，打破与执行服务的构造环） */
    private final ITestFlowExecutionService testFlowExecutionService;

    public EnvRunSlotLifecycle(
            EnvRunConcurrencyService concurrencyService,
            ITestFlowRunService testFlowRunService,
            ITestFlowService testFlowService,
            @Lazy ITestFlowExecutionService testFlowExecutionService) {
        this.concurrencyService = concurrencyService;
        this.testFlowRunService = testFlowRunService;
        this.testFlowService = testFlowService;
        this.testFlowExecutionService = testFlowExecutionService;
    }

    /**
     * Run 进入终态（通过 / 失败 / 中止）后调用。
     * 顺序：从运行中心活动列表移除该 Run → 释放环境槽 → 尝试开跑下一项排队。
     *
     * @param testFlowRunId 刚结束的 Run 主键
     * @param projectIdHint 已知项目 id；为空时根据 Run 所属测试流反查
     */
    public void onRunTerminal(Long testFlowRunId, Long projectIdHint) {
        if (testFlowRunId == null) {
            return;
        }
        TestFlowRun run = testFlowRunService.selectTestFlowRunById(testFlowRunId);
        Long envId = run != null ? run.getTestProjectEnvId() : null;
        Long projectId = projectIdHint != null ? projectIdHint : resolveProjectId(run);
        if (projectId != null) {
            concurrencyService.markRunFinished(projectId, testFlowRunId);
        }
        concurrencyService.releaseEnvSlot(envId, testFlowRunId);
        promoteAfterRelease(envId);
    }

    /**
     * 环境槽空出后：弹出队首排队项，带「跳过再次入队」标记触发开跑。
     * 开跑成功则把 runId 绑定到原 queueItemId，供前端 / 工具轮询；
     * 触发失败则标记该排队项已取消，并继续尝试下一项。
     *
     * @param envId 刚释放的测试环境 id
     */
    public void promoteAfterRelease(Long envId) {
        if (envId == null) {
            return;
        }
        EnvRunQueueItem next = concurrencyService.pollNext(envId);
        if (next == null || next.getTriggerParams() == null) {
            return;
        }
        TriggerTestFlowRunParams params = next.getTriggerParams();
        // 提升开跑时不再二次入队，但仍需抢占环境槽
        params.setBypassEnvQueue(true);
        if (params.getOperatorUserId() == null) {
            params.setOperatorUserId(next.getOperatorUserId());
        }
        try {
            var outcome = testFlowExecutionService.triggerRun(params);
            if (outcome != null && outcome.getRunId() != null) {
                concurrencyService.bindQueueItemRunId(next.getQueueItemId(), outcome.getRunId());
                return;
            }
            // bypass 路径不应再入队；若异常返回 queued / 空结果，解绑等待方并尝试下一项
            if (outcome != null && outcome.isQueued()) {
                log.warn("环境排队提升后再次入队 envId={} oldQ={} newQ={}",
                        envId, next.getQueueItemId(), outcome.getQueueItemId());
            } else {
                log.warn("环境排队提升未返回 runId envId={} queueItemId={}",
                        envId, next.getQueueItemId());
            }
            concurrencyService.bindQueueItemCancelled(next.getQueueItemId());
            promoteAfterRelease(envId);
        } catch (Exception e) {
            log.warn("环境排队提升开跑失败 envId={} queueItemId={}: {}",
                    envId, next.getQueueItemId(), e.getMessage());
            concurrencyService.bindQueueItemCancelled(next.getQueueItemId());
            promoteAfterRelease(envId);
        }
    }

    /** 由 Run 记录上的测试流 id 反查所属项目 */
    private Long resolveProjectId(TestFlowRun run) {
        if (run == null || run.getTestFlowId() == null) {
            return null;
        }
        TestFlow flow = testFlowService.selectTestFlowById(run.getTestFlowId());
        return flow != null ? flow.getTestProjectId() : null;
    }
}
