package com.qualitest.flow.run;

import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Run 与步骤的短事务落库。
 * <p>
 * 每步单独短事务 insert，避免整次跑流包进一个大事务；步骤在节点完成后即可被详情查询读到。
 */
@Service
@RequiredArgsConstructor
public class RunPersistenceService {

    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowRunStepService testFlowRunStepService;

    /** 写入单条运行步骤（短事务立刻提交） */
    @Transactional(rollbackFor = Exception.class)
    public void insertStep(TestFlowRunStep step) {
        if (step.getCreateTime() == null) {
            step.setCreateTime(DateUtils.getNowDate());
        }
        testFlowRunStepService.insertTestFlowRunStep(step);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRun(TestFlowRun run) {
        if (run.getUpdateTime() == null) {
            run.setUpdateTime(DateUtils.getNowDate());
        }
        testFlowRunService.updateTestFlowRun(run);
    }

    /** CAS：仅 paused 可切 running，返回是否更新成功 */
    @Transactional(rollbackFor = Exception.class)
    public boolean casMarkRunningFromPaused(Long testFlowRunId) {
        return testFlowRunService.updateStatusFromPausedToRunning(testFlowRunId) > 0;
    }
}
