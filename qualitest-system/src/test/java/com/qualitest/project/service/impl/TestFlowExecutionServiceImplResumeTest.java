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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
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
 * {@link TestFlowExecutionServiceImpl} resume 相关单测：Run 详情的 pauseInfo 与 resumeRun API 封装。
 * <p>
 * Mock 各 Service 与 {@link TestFlowExecutor}；SecurityContext 注入登录用户供 resumeRun 取 operator。
 * <p>
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=TestFlowExecutionServiceImplResumeTest
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
     * paused Run 的 getRunDetail。
     * 期望：返回 pauseInfo，含快照栈与可选决策列表。
     */
    @Test
    @Order(1)
    void getRunDetail_buildsPauseInfoWithSnapshotStackItems() {
        begin("getRunDetail_buildsPauseInfoWithSnapshotStackItems");
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
        log("pauseNodeId=n2 stackSize=1 decisions=" + pauseInfo.getAvailableDecisions());
        end("getRunDetail_buildsPauseInfoWithSnapshotStackItems");
    }

    /**
     * 非 paused Run 的 getRunDetail。
     * 期望：pauseInfo 为 null。
     */
    @Test
    @Order(2)
    void getRunDetail_pauseInfoNullWhenNotPaused() {
        begin("getRunDetail_pauseInfoNullWhenNotPaused");
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
        log("pauseInfo=null");
        end("getRunDetail_pauseInfoNullWhenNotPaused");
    }

    /**
     * 对非 paused Run 调用 resumeRun。
     * 期望：委托 Executor 幂等结果并带回 TF_RUN_NOT_PAUSED。
     */
    @Test
    @Order(3)
    void resumeRun_idempotentWhenNotPaused() {
        begin("resumeRun_idempotentWhenNotPaused");
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
        log("idempotent=true errorCode=" + result.getErrorCode());
        end("resumeRun_idempotentWhenNotPaused");
    }
}
