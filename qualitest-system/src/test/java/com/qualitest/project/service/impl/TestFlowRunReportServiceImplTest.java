package com.qualitest.project.service.impl;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.report.RunHtmlReportFile;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 测谁：TestFlowRunReportServiceImpl 导出校验与报告产物。
 * 边界：running/paused 拒绝；failed 可导出且文件名含流名状态。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowRunReportServiceImplTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowRunReportServiceImplTest {

    @Mock
    private ITestFlowExecutionService testFlowExecutionService;
    @Mock
    private ITestFlowService testFlowService;
    @Mock
    private ITestProjectService testProjectService;

    @InjectMocks
    private TestFlowRunReportServiceImpl service;

    @Test
    @Order(1)
    @DisplayName("执行中的运行拒绝导出")
    void rejectRunning() {
        // 前提：运行状态为 running
        // 期望：抛出业务异常
        when(testFlowExecutionService.getRunDetail(1L)).thenReturn(detail("running"));
        assertThrows(ServiceException.class, () -> service.buildHtmlReport(1L));
    }

    @Test
    @Order(2)
    @DisplayName("暂停中的运行拒绝导出")
    void rejectPaused() {
        // 前提：运行状态为 paused
        // 期望：抛出业务异常
        when(testFlowExecutionService.getRunDetail(2L)).thenReturn(detail("paused"));
        assertThrows(ServiceException.class, () -> service.buildHtmlReport(2L));
    }

    @Test
    @Order(3)
    @DisplayName("失败运行可导出，HTML 与文件名含流名状态")
    void exportFailed() {
        // 前提：失败运行，项目名「项目B」、流名「流A」
        // 期望：HTML 含 FAILED/流A/项目B；文件名含流A、FAILED、运行 ID
        when(testFlowExecutionService.getRunDetail(3L)).thenReturn(detail("failed"));
        when(testFlowService.selectTestFlowById(10L)).thenReturn(
                TestFlow.builder().testFlowId(10L).testProjectId(20L).flowName("流A").build());
        when(testProjectService.selectTestProjectById(20L)).thenReturn(
                TestProject.builder().testProjectId(20L).projectName("项目B").build());
        RunHtmlReportFile report = service.buildHtmlReport(3L);
        assertTrue(report.getHtml().contains("FAILED"));
        assertTrue(report.getHtml().contains("流A"));
        assertTrue(report.getHtml().contains("项目B"));
        assertTrue(report.getFileName().contains("流A"));
        assertTrue(report.getFileName().contains("FAILED"));
        assertTrue(report.getFileName().contains("3"));
        assertTrue(report.getFileName().endsWith(".html"));
    }

    /** 构造仅含状态的运行详情 */
    private static TestFlowRunDetailResult detail(String status) {
        return TestFlowRunDetailResult.builder()
                .run(TestFlowRunResult.builder()
                        .testFlowRunId(3L)
                        .testFlowId(10L)
                        .status(status)
                        .finishedAt(new Date())
                        .build())
                .steps(List.of())
                .build();
    }
}
