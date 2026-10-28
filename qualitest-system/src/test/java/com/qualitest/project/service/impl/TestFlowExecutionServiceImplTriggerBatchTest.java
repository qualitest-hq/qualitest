package com.qualitest.project.service.impl;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.RunStatusUpdater;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.run.TestFlowExecutor;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.TriggerTestFlowRunBatchItemParams;
import com.qualitest.project.params.TriggerTestFlowRunBatchParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TriggerTestFlowRunBatchResult;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 测 TestFlowExecutionServiceImpl.triggerBatch：串行 trigger + await、失败即停与同流多场景。
 * 边界：spy 掉 triggerRun / awaitRunTerminal，不跑真实图。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowExecutionServiceImplTriggerBatchTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowExecutionServiceImplTriggerBatchTest {

    private TestFlowExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        TestFlowExecutionServiceImpl real = new TestFlowExecutionServiceImpl(
                mock(ITestFlowService.class),
                mock(ITestFlowRunService.class),
                mock(ITestFlowRunStepService.class),
                mock(ITestProjectEnvService.class),
                mock(ITestProjectMemberService.class),
                mock(TestProjectMapper.class),
                mock(TestFlowExecutor.class),
                new StepResultWriter(),
                mock(FlowRunReadinessGate.class),
                mock(RunStatusUpdater.class),
                mock(com.qualitest.flow.sync.FlowExternalChangePublisher.class)
        );
        service = spy(real);
    }

    /**
     * 前提：两条均 await 为 passed。
     * 期望：两项通过、未提前停、triggerType 默认 suite。
     */
    @Test
    @Order(1)
    @DisplayName("全部通过时汇总 passedCount=2")
    void triggerBatch_allPassed() {
        doReturn(11L).doReturn(22L).when(service).triggerRun(any(TriggerTestFlowRunParams.class));
        doReturn(passed(11L, "sc-a", 100L))
                .doReturn(passed(22L, "sc-b", 200L))
                .when(service).awaitRunTerminal(anyLong(), anyLong());

        TriggerTestFlowRunBatchResult result = service.triggerBatch(TriggerTestFlowRunBatchParams.builder()
                .items(List.of(
                        item(1L, "sc-a"),
                        item(2L, "sc-b")))
                .stopOnNonPass(true)
                .build());

        assertEquals(2, result.getItems().size());
        assertEquals(2, result.getPassedCount());
        assertFalse(result.isStoppedEarly());
        assertEquals(RunStatus.PASSED.getCode(), result.getItems().get(0).getStatus());
        assertEquals(11L, result.getItems().get(0).getRunId());

        ArgumentCaptor<TriggerTestFlowRunParams> captor = ArgumentCaptor.forClass(TriggerTestFlowRunParams.class);
        verify(service, times(2)).triggerRun(captor.capture());
        assertEquals("suite", captor.getAllValues().get(0).getTriggerType());
        assertEquals("sc-a", captor.getAllValues().get(0).getRunScenarioId());
        assertEquals("sc-b", captor.getAllValues().get(1).getRunScenarioId());
    }

    /**
     * 前提：首条 failed，stopOnNonPass=true，队列还有第二项。
     * 期望：提前停，第二项 status=skipped，不再 await。
     */
    @Test
    @Order(2)
    @DisplayName("非通过即停时后续标记 skipped")
    void triggerBatch_stopsOnFailed() {
        doReturn(11L).when(service).triggerRun(any(TriggerTestFlowRunParams.class));
        doReturn(TestFlowRunResult.builder()
                .testFlowRunId(11L)
                .status(RunStatus.FAILED.getCode())
                .errorMessage("断言失败")
                .build())
                .when(service).awaitRunTerminal(eq(11L), anyLong());

        TriggerTestFlowRunBatchResult result = service.triggerBatch(TriggerTestFlowRunBatchParams.builder()
                .items(List.of(item(1L, "sc-a"), item(2L, "sc-b")))
                .stopOnNonPass(true)
                .build());

        assertTrue(result.isStoppedEarly());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(RunStatus.SKIPPED.getCode(), result.getItems().get(1).getStatus());
        verify(service, times(1)).triggerRun(any(TriggerTestFlowRunParams.class));
        verify(service, times(1)).awaitRunTerminal(anyLong(), anyLong());
    }

    /**
     * 前提：同流两个场景；首条 failed，stopOnNonPass=false。
     * 期望：仍触发第二场景，failedCount=1 passedCount=1。
     */
    @Test
    @Order(3)
    @DisplayName("同流两场景且继续跑完")
    void triggerBatch_sameFlowTwoScenarios_continue() {
        doReturn(11L).doReturn(22L).when(service).triggerRun(any(TriggerTestFlowRunParams.class));
        doReturn(TestFlowRunResult.builder()
                .testFlowRunId(11L)
                .status(RunStatus.FAILED.getCode())
                .runScenarioId("sc-1")
                .build())
                .doReturn(passed(22L, "sc-2", 50L))
                .when(service).awaitRunTerminal(anyLong(), anyLong());

        TriggerTestFlowRunBatchResult result = service.triggerBatch(TriggerTestFlowRunBatchParams.builder()
                .items(List.of(item(9L, "sc-1"), item(9L, "sc-2")))
                .stopOnNonPass(false)
                .build());

        assertFalse(result.isStoppedEarly());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getPassedCount());
        assertEquals(2, result.getItems().size());
        verify(service, times(2)).triggerRun(any(TriggerTestFlowRunParams.class));
    }

    /**
     * 前提：首条 trigger 抛 ServiceException，stopOnNonPass=true。
     * 期望：该项 failed，后续 skipped。
     */
    @Test
    @Order(4)
    @DisplayName("触发失败记入 item 并按策略停止")
    void triggerBatch_triggerException_stops() {
        doThrow(new ServiceException("图未就绪"))
                .when(service).triggerRun(any(TriggerTestFlowRunParams.class));

        TriggerTestFlowRunBatchResult result = service.triggerBatch(TriggerTestFlowRunBatchParams.builder()
                .testFlowIds(List.of(1L, 2L))
                .stopOnNonPass(true)
                .build());

        assertTrue(result.isStoppedEarly());
        assertEquals(RunStatus.FAILED.getCode(), result.getItems().get(0).getStatus());
        assertEquals("图未就绪", result.getItems().get(0).getErrorMessage());
        assertEquals(RunStatus.SKIPPED.getCode(), result.getItems().get(1).getStatus());
        verify(service, times(1)).triggerRun(any(TriggerTestFlowRunParams.class));
    }

    /**
     * 前提：items 与 testFlowIds 皆空。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(5)
    @DisplayName("空队列拒绝")
    void triggerBatch_emptyQueue_rejects() {
        assertThrows(ServiceException.class, () ->
                service.triggerBatch(TriggerTestFlowRunBatchParams.builder().build()));
    }

    private static TriggerTestFlowRunBatchItemParams item(Long flowId, String scenarioId) {
        return TriggerTestFlowRunBatchItemParams.builder()
                .testFlowId(flowId)
                .runScenarioId(scenarioId)
                .build();
    }

    private static TestFlowRunResult passed(Long runId, String scenarioId, Long durationMs) {
        return TestFlowRunResult.builder()
                .testFlowRunId(runId)
                .status(RunStatus.PASSED.getCode())
                .runScenarioId(scenarioId)
                .durationMs(durationMs)
                .build();
    }
}
