package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextBuilder;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.context.RunScenarioBootstrap;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.flow.run.ExecutionOutcome;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.ResumeDecision;
import com.qualitest.flow.run.RunExecutionState;
import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.RunPauseInfo;
import com.qualitest.project.result.SnapshotStackItemResult;
import com.qualitest.flow.run.RunBootstrapMeta;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.run.TestFlowExecutor;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.http.FlowExternalPermission;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 测试流 Run 编排服务。
 * <p>
 * {@link #triggerRun}：校验成员与图 → 解析/校验 graph_json → 解析场景 → 固化 snapshot →
 * 创建 Run 记录 → 同步调用 {@link TestFlowExecutor}。
 * {@link #getRunDetail}：返回 Run 头 + 按 step_index 排序的步骤列表。
 */
@Service
@RequiredArgsConstructor
public class TestFlowExecutionServiceImpl implements ITestFlowExecutionService {

    private final ITestFlowService testFlowService;
    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowRunStepService testFlowRunStepService;
    private final ITestProjectEnvService testProjectEnvService;
    private final ITestProjectMemberService testProjectMemberService;
    private final TestProjectMapper testProjectMapper;
    private final TestFlowExecutor testFlowExecutor;
    private final StepResultWriter stepResultWriter;
    private final GraphJsonValidator graphJsonValidator = new GraphJsonValidator();

    @Override
    public Long triggerRun(TriggerTestFlowRunParams params) {
        if (params == null || params.getTestFlowId() == null) {
            throw new ServiceException("testFlowId 不能为空");
        }

        // 1. 加载测试流并校验项目成员
        TestFlow testFlow = testFlowService.selectTestFlowById(params.getTestFlowId());
        if (testFlow == null || (testFlow.getDelStatus() != null && testFlow.getDelStatus() != 0)) {
            throw new ServiceException("测试流不存在");
        }
        if (StrUtil.isBlank(testFlow.getGraphJson())) {
            throw new ServiceException("测试流 graph_json 为空");
        }

        TestProjectMemberRole memberRole = testProjectMemberService.getCheckProjectMemberRole(testFlow.getTestProjectId());

        // 2. 图解析与结构校验（失败则不创建 Run）
        GraphJson graph;
        try {
            graph = GraphJson.parse(testFlow.getGraphJson());
            if (graph == null) {
                throw new IllegalArgumentException("解析结果为空");
            }
        } catch (Exception e) {
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, "图解析失败: " + e.getMessage());
        }

        GraphValidationResult validation = graphJsonValidator.validate(graph);
        if (!validation.isOk()) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    String.join("; ", validation.getErrors())
            );
        }

        // 3. 解析运行场景与环境，组装运行时上下文
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(
                graph,
                params.getRunScenarioId(),
                params.getTestProjectEnvId()
        );

        TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(scenario.getTestProjectEnvId());
        if (env == null || (env.getDelStatus() != null && env.getDelStatus() != 0)) {
            throw new ServiceException("测试环境不存在");
        }
        if (!testFlow.getTestProjectId().equals(env.getTestProjectId())) {
            throw new ServiceException("环境与测试流不属于同一项目");
        }

        String assetJson = testProjectMapper.selectAssetVariablesByTestProjectId(testFlow.getTestProjectId());
        // 加载项目响应约定写入 Run 上下文，供本 Run 内 HTTP 节点校验业务码
        TestProject project = testProjectMapper.selectTestProjectById(testFlow.getTestProjectId());
        boolean externalPermitted = FlowExternalPermission.isPermitted(memberRole);
        FlowRunContext ctx = FlowRunContextBuilder.build(
                env, assetJson, scenario.getFlowSeed(), testFlow.getTestProjectId(),
                externalPermitted);
        if (project != null) {
            ctx.setResponseConvention(project.getResponseConvention());
            ctx.setProjectAuthConfig(project.getAuthConfig());
        }

        // 4. 深拷贝图 JSON 为 snapshot，并计算指纹供列表对比
        String snapshotJson = graph.toJsonString();
        String fingerprint = StepResultWriter.fingerprint(snapshotJson);

        // 5. 插入 running 态 Run，再同步执行
        Date now = DateUtils.getNowDate();
        Long runId = IdUtil.getSnowflakeNextId();
        TestFlowRun run = TestFlowRun.builder()
                .testFlowRunId(runId)
                .testFlowId(testFlow.getTestFlowId())
                .testProjectEnvId(scenario.getTestProjectEnvId())
                .runScenarioId(scenario.getScenarioId())
                .status(RunStatus.RUNNING)
                .graphJsonSnapshot(snapshotJson)
                .graphFingerprint(fingerprint)
                .startedAt(now)
                .triggerType(StrUtil.blankToDefault(params.getTriggerType(), "manual"))
                .delStatus(0)
                .build();
        run.setCreateTime(now);
        testFlowRunService.insertTestFlowRun(run);

        testFlowExecutor.execute(runId, graph, ctx, new RunBootstrapMeta(scenario, env.getEnvName(), env));
        return runId;
    }

    @Override
    public TestFlowRunDetailResult getRunDetail(Long testFlowRunId) {
        if (testFlowRunId == null) {
            throw new ServiceException("testFlowRunId 不能为空");
        }
        TestFlowRunResult run = testFlowRunService.selectTestFlowRunResult(testFlowRunId);
        if (run == null) {
            throw new ServiceException("运行记录不存在");
        }

        TestFlow testFlow = testFlowService.selectTestFlowById(run.getTestFlowId());
        if (testFlow != null) {
            testProjectMemberService.getCheckProjectMemberRole(testFlow.getTestProjectId());
        }

        TestFlowRunStepParams stepParams = TestFlowRunStepParams.builder()
                .testFlowRunId(testFlowRunId)
                .build();
        List<TestFlowRunStepResult> steps = testFlowRunStepService.selectTestFlowRunStepResultList(stepParams)
                .stream()
                .sorted((a, b) -> Long.compare(
                        a.getStepIndex() != null ? a.getStepIndex() : 0L,
                        b.getStepIndex() != null ? b.getStepIndex() : 0L
                ))
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        if (!stepResultWriter.hasRunConfigStep(steps)) {
            String envName = null;
            if (run.getTestProjectEnvId() != null) {
                TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(run.getTestProjectEnvId());
                if (env != null) {
                    envName = env.getEnvName();
                }
            }
            steps.add(0, stepResultWriter.buildFallbackRunConfigStepResult(run, envName));
        }

        return TestFlowRunDetailResult.builder()
                .run(run)
                .steps(steps)
                .pauseInfo(buildPauseInfo(run))
                .build();
    }

    @Override
    public ResumeRunResult resumeRun(Long testFlowRunId, ResumeTestFlowRunParams params) {
        if (testFlowRunId == null) {
            throw new ServiceException("testFlowRunId 不能为空");
        }
        TestFlowRun run = testFlowRunService.selectTestFlowRunById(testFlowRunId);
        if (run == null || (run.getDelStatus() != null && run.getDelStatus() != 0)) {
            throw new ServiceException("运行记录不存在");
        }

        TestFlow testFlow = testFlowService.selectTestFlowById(run.getTestFlowId());
        if (testFlow != null) {
            testProjectMemberService.getCheckProjectMemberRole(testFlow.getTestProjectId());
        }

        TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(run.getTestProjectEnvId());
        if (env == null || (env.getDelStatus() != null && env.getDelStatus() != 0)) {
            throw new ServiceException("测试环境不存在");
        }

        ResumeDecision decision = ResumeDecision.builder()
                .decision(params != null ? params.getDecision() : null)
                .snapshotId(params != null ? params.getSnapshotId() : null)
                .operator(SecurityUtils.getUsername())
                .build();

        ExecutionOutcome outcome = testFlowExecutor.resume(testFlowRunId, decision, env);

        TestFlowRunResult latest = testFlowRunService.selectTestFlowRunResult(testFlowRunId);
        return ResumeRunResult.builder()
                .testFlowRunId(testFlowRunId)
                .status(latest != null ? latest.getStatus() : outcome.getTerminalStatus())
                .idempotent(outcome.isIdempotent())
                .errorCode(outcome.getErrorCode())
                .errorMessage(outcome.getErrorMessage())
                .build();
    }

    /**
     * 从 paused 的 Run 记录组装 API 用的暂停信息；非 paused 返回 null。
     */
    private RunPauseInfo buildPauseInfo(TestFlowRunResult run) {
        if (run == null || !RunStatus.PAUSED.equals(run.getStatus())) {
            return null;
        }
        RunExecutionState state = RunExecutionState.fromJson(run.getRunExecutionState());
        if (state == null) {
            return null;
        }
        List<SnapshotStackItemResult> stackItems = new java.util.ArrayList<>();
        if (state.getSnapshotStack() != null) {
            state.getSnapshotStack().forEach(entry -> stackItems.add(SnapshotStackItemResult.builder()
                    .nodeId(entry.getNodeId())
                    .snapshotId(entry.getSnapshotId())
                    .build()));
        }
        return RunPauseInfo.builder()
                .pauseReason(state.getPauseReason())
                .pauseNodeId(state.getPauseNodeId())
                .currentNodeId(state.getCurrentNodeId())
                .snapshotStack(stackItems)
                .pausedAt(run.getPausedAt())
                .availableDecisions(Arrays.asList(
                        ResumeDecision.RESTORE_AND_RETRY,
                        ResumeDecision.RETRY_IN_PLACE,
                        ResumeDecision.SKIP,
                        ResumeDecision.ABORT
                ))
                .build();
    }
}
