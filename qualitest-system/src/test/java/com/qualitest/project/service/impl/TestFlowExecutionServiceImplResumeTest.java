package com.qualitest.project.service.impl;

import com.qualitest.common.core.domain.model.LoginUser;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.run.ExecutionOutcome;
import com.qualitest.flow.run.ResumeDecision;
import com.qualitest.flow.run.RunExecutionState;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.run.TestFlowExecutor;
import com.qualitest.flow.snapshot.SnapshotStackEntry;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.RunPauseInfo;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 TestFlowExecutionServiceImpl.resume：Run 详情的 pauseInfo 与 resumeRun API 封装。
 * 边界：Mock 各 Service 与 TestFlowExecutor；SecurityContext 注入登录用户。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowExecutionServiceImplResumeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowExecutionServiceImplResumeTest {

    private ITestFlowRunService testFlowRunService;
    private ITestFlowService testFlowService;
    private ITestFlowRunStepService testFlowRunStepService;
    private ITestProjectEnvService testProjectEnvService;
    private ITestProjectMemberService testProjectMemberService;
    private TestFlowExecutor testFlowExecutor;
    private StepResultWriter stepResultWriter;
    private TestFlowExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        testFlowRunService = mock(ITestFlowRunService.class);
        testFlowService = mock(ITestFlowService.class);
        testFlowRunStepService = mock(ITestFlowRunStepService.class);
        testProjectEnvService = mock(ITestProjectEnvService.class);
        testProjectMemberService = mock(ITestProjectMemberService.class);
        testFlowExecutor = mock(TestFlowExecutor.class);
        stepResultWriter = new StepResultWriter();

        service = new TestFlowExecutionServiceImpl(
                testFlowService,
                testFlowRunService,
                testFlowRunStepService,
                testProjectEnvService,
                testProjectMemberService,
                null,
                testFlowExecutor,
                stepResultWriter,
                null
        );

        LoginUser loginUser = mock(LoginUser.class);
        when(loginUser.getUsername()).thenReturn("tester");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 前提：Run 状态 paused，executionState 含 pauseNode=n2 与 snap-1。
     * 期望：pauseInfo 含节点、栈、pausedAt 与 RESTORE_AND_RETRY 决策。
     */
    @Test
    @Order(1)
    @DisplayName("paused Run 详情含 pauseInfo 与快照栈")
    void getRunDetail_buildsPauseInfoWithSnapshotStackItems() {
        Date pausedAt = DateUtils.getNowDate();
        RunExecutionState state = RunExecutionState.builder()
                .pauseReason(RunExecutionState.PAUSE_REASON_NODE_FAILURE)
                .pauseNodeId("n2")
                .currentNodeId("n2")
                .snapshotStack(List.of(new SnapshotStackEntry("n1", "snap-1")))
                .build();

        TestFlowRunResult run = TestFlowRunResult.builder()
                .testFlowRunId(10L)
                .testFlowId(1L)
                .status(RunStatus.PAUSED)
                .runExecutionState(state.toJson())
                .pausedAt(pausedAt)
                .build();

        when(testFlowRunService.selectTestFlowRunResult(10L)).thenReturn(run);
        when(testFlowService.selectTestFlowById(1L)).thenReturn(TestFlow.builder().testProjectId(99L).build());
        when(testFlowRunStepService.selectTestFlowRunStepResultList(any(TestFlowRunStepParams.class)))
                .thenReturn(List.of(stepResultWriter.buildFallbackRunConfigStepResult(
                        TestFlowRunResult.builder().testFlowRunId(10L).runScenarioId("sc-default").build(),
                        "env")));

        TestFlowRunDetailResult detail = service.getRunDetail(10L);

        RunPauseInfo pauseInfo = detail.getPauseInfo();
        assertNotNull(pauseInfo);
        assertEquals("n2", pauseInfo.getPauseNodeId());
        assertEquals(1, pauseInfo.getSnapshotStack().size());
        assertEquals("n1", pauseInfo.getSnapshotStack().get(0).getNodeId());
        assertEquals("snap-1", pauseInfo.getSnapshotStack().get(0).getSnapshotId());
        assertEquals(pausedAt, pauseInfo.getPausedAt());
        assertTrue(pauseInfo.getAvailableDecisions().contains(ResumeDecision.RESTORE_AND_RETRY));
    }

    /**
     * 前提：Run 状态为 passed。
     * 期望：pauseInfo 为 null。
     */
    @Test
    @Order(2)
    @DisplayName("非 paused 时 pauseInfo 为 null")
    void getRunDetail_pauseInfoNullWhenNotPaused() {
        TestFlowRunResult run = TestFlowRunResult.builder()
                .testFlowRunId(11L)
                .testFlowId(1L)
                .status(RunStatus.PASSED)
                .build();

        when(testFlowRunService.selectTestFlowRunResult(11L)).thenReturn(run);
        when(testFlowService.selectTestFlowById(1L)).thenReturn(TestFlow.builder().testProjectId(99L).build());
        when(testFlowRunStepService.selectTestFlowRunStepResultList(any(TestFlowRunStepParams.class)))
                .thenReturn(Collections.emptyList());

        TestFlowRunDetailResult detail = service.getRunDetail(11L);

        assertNull(detail.getPauseInfo());
    }

    /**
     * 前提：对已 passed 的 Run 调用 resumeRun；Executor 返回幂等结果。
     * 期望：idempotent=true；errorCode=TF_RUN_NOT_PAUSED。
     */
    @Test
    @Order(3)
    @DisplayName("非 paused resume 返回幂等结果")
    void resumeRun_idempotentWhenNotPaused() {
        TestFlowRun run = TestFlowRun.builder()
                .testFlowRunId(20L)
                .testFlowId(2L)
                .testProjectEnvId(100L)
                .status(RunStatus.PASSED)
                .delStatus(0)
                .build();
        TestProjectEnv env = TestProjectEnv.builder().delStatus(0).build();

        when(testFlowRunService.selectTestFlowRunById(20L)).thenReturn(run);
        when(testFlowService.selectTestFlowById(2L)).thenReturn(TestFlow.builder().testProjectId(99L).build());
        when(testProjectEnvService.selectTestProjectEnvById(100L)).thenReturn(env);
        when(testFlowExecutor.resume(eq(20L), any(ResumeDecision.class), eq(env)))
                .thenReturn(ExecutionOutcome.idempotent(RunStatus.PASSED));
        when(testFlowRunService.selectTestFlowRunResult(20L)).thenReturn(
                TestFlowRunResult.builder().testFlowRunId(20L).status(RunStatus.PASSED).build());

        ResumeRunResult result = service.resumeRun(20L,
                ResumeTestFlowRunParams.builder().decision(ResumeDecision.RETRY_IN_PLACE).build());

        assertTrue(result.isIdempotent());
        assertEquals(FlowErrorCode.TF_RUN_NOT_PAUSED.getCode(), result.getErrorCode());
        assertEquals(RunStatus.PASSED, result.getStatus());
        verify(testFlowExecutor).resume(eq(20L), any(ResumeDecision.class), eq(env));
    }
}
