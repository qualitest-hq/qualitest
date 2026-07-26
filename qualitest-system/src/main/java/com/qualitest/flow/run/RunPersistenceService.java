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
 * Run 与步骤的短事务落库，供 {@link TestFlowExecutor} 在 HTTP snapshot/restore 事务外调用。
 */
@Service
@RequiredArgsConstructor
public class RunPersistenceService {

    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowRunStepService testFlowRunStepService;

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
