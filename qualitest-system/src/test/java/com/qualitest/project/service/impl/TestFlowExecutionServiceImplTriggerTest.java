package com.qualitest.project.service.impl;

import com.qualitest.common.core.domain.model.LoginUser;
import com.qualitest.flow.run.ExecutionOutcome;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.RunStatusUpdater;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.run.TestFlowExecutor;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.TriggerTestFlowRunParams;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 TestFlowExecutionServiceImpl.triggerRun：先写 running 并立刻返回 runId，后台再执行。
 * 边界：Mock 落库与 Executor；用 Latch 证明返回不堵到整次结束。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowExecutionServiceImplTriggerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowExecutionServiceImplTriggerTest {

    private ITestFlowRunService testFlowRunService;
    private ITestFlowService testFlowService;
    private ITestProjectEnvService testProjectEnvService;
    private ITestProjectMemberService testProjectMemberService;
    private TestProjectMapper testProjectMapper;
    private TestFlowExecutor testFlowExecutor;
    private FlowRunReadinessGate flowRunReadinessGate;
    private TestFlowExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        testFlowRunService = mock(ITestFlowRunService.class);
        testFlowService = mock(ITestFlowService.class);
        testProjectEnvService = mock(ITestProjectEnvService.class);
        testProjectMemberService = mock(ITestProjectMemberService.class);
        testProjectMapper = mock(TestProjectMapper.class);
        testFlowExecutor = mock(TestFlowExecutor.class);
        flowRunReadinessGate = mock(FlowRunReadinessGate.class);
        when(flowRunReadinessGate.collectBlockingErrors(any(), any())).thenReturn(List.of());

        service = new TestFlowExecutionServiceImpl(
                testFlowService,
                testFlowRunService,
                mock(ITestFlowRunStepService.class),
                testProjectEnvService,
                testProjectMemberService,
                testProjectMapper,
                testFlowExecutor,
                new StepResultWriter(),
                flowRunReadinessGate,
                mock(RunStatusUpdater.class),
                mock(com.qualitest.flow.sync.FlowExternalChangePublisher.class)
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
     * 前提：合法图与环境；Executor.execute 阻塞至 latch 释放。
     * 期望：triggerRun 在 execute 结束前返回 runId；已 insert running。
     */
    @Test
    @Order(1)
    @DisplayName("triggerRun 立即返回 runId 且后台执行")
    void triggerRun_returnsBeforeExecuteFinishes() throws Exception {
        String graphJson = loadResource("flow/linear-run-graph.json");
        TestFlow flow = TestFlow.builder()
                .testFlowId(1L)
                .testProjectId(99L)
                .graphJson(graphJson)
                .delStatus(0)
                .build();
        when(testFlowService.selectTestFlowById(1L)).thenReturn(flow);
        when(testProjectMemberService.getCheckProjectMemberRole(eq(99L), any())).thenReturn(TestProjectMemberRole.OWNER);

        TestProjectEnv env = TestProjectEnv.builder()
                .testProjectEnvId(9001L)
                .testProjectId(99L)
                .envName("开发")
                .envUrl("http://localhost:8801")
                .delStatus(0)
                .build();
        when(testProjectEnvService.selectTestProjectEnvById(9001L)).thenReturn(env);
        when(testProjectMapper.selectAssetVariablesByTestProjectId(99L)).thenReturn("[]");
        when(testProjectMapper.selectTestProjectById(99L)).thenReturn(null);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        doAnswer(inv -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            finished.set(true);
            return ExecutionOutcome.passed(1);
        }).when(testFlowExecutor).execute(anyLong(), any(), any(), any());

        Long runId = service.triggerRun(TriggerTestFlowRunParams.builder()
                .testFlowId(1L)
                .triggerType("manual")
                .build());

        assertNotNull(runId);
        assertFalse(finished.get());
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        ArgumentCaptor<TestFlowRun> runCaptor = ArgumentCaptor.forClass(TestFlowRun.class);
        verify(testFlowRunService).insertTestFlowRun(runCaptor.capture());
        assertEquals(RunStatus.RUNNING, runCaptor.getValue().getStatus());
        assertEquals(runId, runCaptor.getValue().getTestFlowRunId());
        assertEquals("manual", runCaptor.getValue().getTriggerType());

        release.countDown();
        assertTrue(entered.await(0, TimeUnit.MILLISECONDS));
        // 等后台跑完
        for (int i = 0; i < 50 && !finished.get(); i++) {
            Thread.sleep(20);
        }
        assertTrue(finished.get());
    }

    /**
     * 前提：Run 已非 running。
     * 期望：awaitRunTerminal 立即返回该快照。
     */
    @Test
    @Order(2)
    @DisplayName("awaitRunTerminal 在终态立即返回")
    void awaitRunTerminal_returnsWhenNotRunning() {
        TestFlowRunResult passed = TestFlowRunResult.builder()
                .testFlowRunId(7L)
                .status(RunStatus.PASSED)
                .build();
        when(testFlowRunService.selectTestFlowRunResult(7L)).thenReturn(passed);
        TestFlowRunResult got = service.awaitRunTerminal(7L, 1_000L);
        assertEquals(RunStatus.PASSED, got.getStatus());
        verify(testFlowRunService).selectTestFlowRunResult(eq(7L));
    }

    private static String loadResource(String path) {
        try (InputStream in = TestFlowExecutionServiceImplTriggerTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
