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
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.input.InputFieldTypes;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.common.core.domain.entity.SysUser;
import com.qualitest.common.core.domain.model.LoginUser;
import com.qualitest.flow.run.ExecutionOutcome;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.ResumeDecision;
import com.qualitest.flow.run.RunExecutionState;
import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TriggerTestFlowRunBatchItemParams;
import com.qualitest.project.params.TriggerTestFlowRunBatchParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.RunPauseInfo;
import com.qualitest.project.result.SnapshotStackItemResult;
import com.qualitest.project.result.TriggerTestFlowRunBatchItemResult;
import com.qualitest.project.result.TriggerTestFlowRunBatchResult;
import com.qualitest.project.result.TriggerTestFlowRunOutcome;
import com.qualitest.project.runhub.EnvRunConcurrencyService;
import com.qualitest.project.runhub.EnvRunQueueItem;
import com.qualitest.project.service.IProjectRunHubService;
import com.qualitest.flow.run.RunBootstrapMeta;
import com.qualitest.flow.run.RunStatusUpdater;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.run.TestFlowExecutor;
import com.qualitest.flow.http.FlowExternalPermission;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.enums.TestProjectMemberPerm;
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
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 测试流 Run 编排服务。
 * <p>
 * 触发运行：解析操作者 → 校验项目成员 → 解析图 → 就绪检查 → 解析场景与环境 →
 * 按项目并发模式占环境槽或入队 → 能开跑则写入 running 并立刻返回 started；
 * 后台线程携带操作者身份继续执行，每步完成即落步骤表。<br>
 * 查询详情：返回 Run 头与按 step_index 排序的步骤列表（执行中也可查）。
 * 运行中心活动列表由 {@link IProjectRunHubService} 提供；本类批跑时仅借用其「等待排队开跑」。
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
    private final FlowRunReadinessGate flowRunReadinessGate;
    private final RunStatusUpdater runStatusUpdater;
    private final FlowExternalChangePublisher flowExternalChangePublisher;
    /** 环境串行锁 / 排队 / 活动索引 */
    private final EnvRunConcurrencyService envRunConcurrencyService;
    /** 批跑时等待排队项开跑拿到 runId */
    private final IProjectRunHubService projectRunHubService;

    /** 后台跑流线程池；守护线程，不阻塞触发接口返回 */
    private final ExecutorService runExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "test-flow-run");
        t.setDaemon(true);
        return t;
    });

    /**
     * 触发一次正式 Run。
     * 操作者优先取请求参数；未传则取当前登录用户；都没有则拒绝。
     * 校验通过后：按环境串行时先抢环境槽，抢不到则入队返回 queued（不写库）；
     * 抢到槽或项目为并行时写入 running、登记活动项并后台开跑，返回 started。
     */
    @Override
    public TriggerTestFlowRunOutcome triggerRun(TriggerTestFlowRunParams params) {
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

        // 解析操作者：优先请求显式传入，否则取当前登录用户；都没有则缺少审计用户
        Long operatorUserId = params.getOperatorUserId();
        if (operatorUserId == null) {
            try {
                operatorUserId = SecurityUtils.getUserId();
            } catch (ServiceException e) {
                throw new ServiceException("缺少审计用户/操作者");
            }
        }
        testProjectMemberService.requireCapability(
                testFlow.getTestProjectId(), operatorUserId, TestProjectMemberPerm.FLOW_RUN);

        // 2. 解析图并做运行就绪检查；未通过则不创建 Run 记录
        GraphJson graph;
        try {
            graph = GraphJson.parse(testFlow.getGraphJson());
            if (graph == null) {
                throw new IllegalArgumentException("解析结果为空");
            }
        } catch (Exception e) {
            throw new FlowExecutionException(FlowErrorCode.TF_GRAPH_INVALID, "图解析失败: " + e.getMessage());
        }

        List<String> readinessErrors =
                flowRunReadinessGate.collectBlockingErrors(graph, testFlow.getTestProjectId());
        if (!readinessErrors.isEmpty()) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    String.join("; ", readinessErrors)
            );
        }

        // 3. 解析运行场景与环境。
        TestProjectEnv envQuery = new TestProjectEnv();
        envQuery.setTestProjectId(testFlow.getTestProjectId());
        envQuery.setDelStatus(0);
        List<TestProjectEnv> projectEnvs = testProjectEnvService.selectTestProjectEnvList(envQuery);
        List<RunScenarioBootstrap.EnvHint> hints = new ArrayList<>();
        if (projectEnvs != null) {
            for (TestProjectEnv e : projectEnvs) {
                if (e != null) {
                    hints.add(new RunScenarioBootstrap.EnvHint(e.getTestProjectEnvId(), e.getEnvName()));
                }
            }
        }
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(
                graph,
                params.getRunScenarioId(),
                params.getTestProjectEnvId(),
                null,
                RunScenarioBootstrap.formatAvailableEnvHint(hints)
        );

        TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(scenario.getTestProjectEnvId());
        if (env == null || (env.getDelStatus() != null && env.getDelStatus() != 0)) {
            throw new ServiceException("测试环境不存在");
        }
        if (!testFlow.getTestProjectId().equals(env.getTestProjectId())) {
            throw new ServiceException("环境与测试流不属于同一项目");
        }

        TestProject project = testProjectMapper.selectTestProjectById(testFlow.getTestProjectId());
        // 排队提升开跑时为 true：不再二次入队，但仍要抢环境槽
        boolean bypassQueue = Boolean.TRUE.equals(params.getBypassEnvQueue());
        boolean serialByEnv = project != null
                && envRunConcurrencyService.isSerialByEnv(project.getRunConcurrencyMode());

        // 预先生成 runId：串行占槽用同一 id；入队时此 id 作废，开跑时再生成新的
        Long runId = IdUtil.getSnowflakeNextId();
        if (serialByEnv && !bypassQueue) {
            // 普通触发：抢不到槽则入 Redis 排队，不写 test_flow_run
            if (!envRunConcurrencyService.tryAcquireEnvSlot(env.getTestProjectEnvId(), runId)) {
                TriggerTestFlowRunParams snap = envRunConcurrencyService.snapshotParams(params);
                snap.setOperatorUserId(operatorUserId);
                snap.setTestProjectEnvId(env.getTestProjectEnvId());
                snap.setRunScenarioId(scenario.getScenarioId());
                String queueItemId = envRunConcurrencyService.enqueue(EnvRunQueueItem.builder()
                        .testProjectId(testFlow.getTestProjectId())
                        .testProjectEnvId(env.getTestProjectEnvId())
                        .envName(env.getEnvName())
                        .testFlowId(testFlow.getTestFlowId())
                        .flowName(testFlow.getFlowName())
                        .runScenarioId(scenario.getScenarioId())
                        .triggerType(StrUtil.blankToDefault(params.getTriggerType(), "manual"))
                        .operatorUserId(operatorUserId)
                        .triggerParams(snap)
                        .build());
                return TriggerTestFlowRunOutcome.queued(
                        queueItemId,
                        testFlow.getTestProjectId(),
                        env.getTestProjectEnvId(),
                        "环境「" + env.getEnvName() + "」有任务在跑，已排队");
            }
        } else if (serialByEnv) {
            // 排队提升路径：必须抢到槽，否则视为异常
            if (!envRunConcurrencyService.tryAcquireEnvSlot(env.getTestProjectEnvId(), runId)) {
                throw new ServiceException("环境跑流槽被占用，无法提升排队项");
            }
        }

        String assetJson = testProjectMapper.selectAssetVariablesByTestProjectId(testFlow.getTestProjectId());
        boolean externalPermitted = testProjectMemberService.hasCapability(
                testFlow.getTestProjectId(), operatorUserId, TestProjectMemberPerm.FLOW_EXTERNAL)
                || SecurityUtils.hasPermi(FlowExternalPermission.PERMISSION);
        FlowRunContext ctx = FlowRunContextBuilder.build(
                env, assetJson, scenario.getFlowSeed(), testFlow.getTestProjectId(),
                externalPermitted);
        if (project != null) {
            ctx.setProjectAuthConfig(project.getAuthConfig());
        }

        String snapshotJson = graph.toJsonString();
        String fingerprint = StepResultWriter.fingerprint(snapshotJson);

        Date now = DateUtils.getNowDate();
        String triggerType = StrUtil.blankToDefault(params.getTriggerType(), "manual");
        TestFlowRun run = TestFlowRun.builder()
                .testFlowRunId(runId)
                .testFlowId(testFlow.getTestFlowId())
                .testProjectEnvId(scenario.getTestProjectEnvId())
                .runScenarioId(scenario.getScenarioId())
                .status(RunStatus.RUNNING.getCode())
                .graphJsonSnapshot(snapshotJson)
                .graphFingerprint(fingerprint)
                .startedAt(now)
                .triggerType(triggerType)
                .delStatus(0)
                .build();
        run.setCreateTime(now);
        testFlowRunService.insertTestFlowRun(run);

        // 登记运行中心「执行中」活动项，并续期环境锁
        envRunConcurrencyService.markRunStarted(
                testFlow.getTestProjectId(),
                env.getTestProjectEnvId(),
                env.getEnvName(),
                testFlow.getTestFlowId(),
                testFlow.getFlowName(),
                runId,
                scenario.getScenarioId(),
                triggerType,
                operatorUserId);

        flowExternalChangePublisher.publishRunStarted(
                testFlow.getTestFlowId(),
                testFlow.getTestProjectId(),
                runId,
                FlowExternalChangeSourceHolder.getOrDefault());

        SecurityContext securityContext = securityContextForOperator(operatorUserId);
        RunBootstrapMeta bootstrap = new RunBootstrapMeta(scenario, env.getEnvName(), env);
        GraphJson graphSnapshot = graph;
        FlowRunContext runCtx = ctx;
        Date startedAt = now;
        runExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                testFlowExecutor.execute(runId, graphSnapshot, runCtx, bootstrap);
            } catch (Exception e) {
                try {
                    String msg = e.getMessage() != null ? e.getMessage() : "运行异常";
                    runStatusUpdater.markFinished(runId, RunStatus.FAILED.getCode(), startedAt,
                            startedAt.getTime(), runCtx,
                            FlowErrorCode.TF_STEP_ERROR.getCode(), msg);
                } catch (Exception ignored) {
                    // ignore
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return TriggerTestFlowRunOutcome.started(runId, testFlow.getTestProjectId(), env.getTestProjectEnvId());
    }

    /**
     * 构造跑流后台线程使用的安全上下文。
     * 若当前请求已是同一操作者登录态则复用；否则安装仅含用户 id 的最小登录主体。
     *
     * @param operatorUserId 操作者用户 id
     * @return 可交给后台线程的安全上下文
     */
    private static SecurityContext securityContextForOperator(Long operatorUserId) {
        SecurityContext current = SecurityContextHolder.getContext();
        try {
            if (current != null && current.getAuthentication() != null
                    && current.getAuthentication().getPrincipal() instanceof LoginUser loginUser
                    && operatorUserId.equals(loginUser.getUserId())) {
                return current;
            }
        } catch (Exception ignored) {
            // 无可用登录态时下方新建
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(operatorUserId);
        SysUser stub = new SysUser();
        stub.setUserId(operatorUserId);
        stub.setUserName("operator-" + operatorUserId);
        loginUser.setUser(stub);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        return context;
    }

    private static final long DEFAULT_PER_RUN_TIMEOUT_MS = 600_000L;
    private static final long MAX_PER_RUN_TIMEOUT_MS = 1_800_000L;

    /**
     * 按队列串行触发：每条写入 running 后等待离开 running，再处理下一项。
     * items 优先；为空时用 testFlowIds（每流默认场景）。
     */
    @Override
    public TriggerTestFlowRunBatchResult triggerBatch(TriggerTestFlowRunBatchParams params) {
        if (params == null) {
            throw new ServiceException("批量跑流参数不能为空");
        }
        List<TriggerTestFlowRunBatchItemParams> queue = resolveBatchQueue(params);
        if (queue.isEmpty()) {
            throw new ServiceException("批量跑流队列不能为空");
        }

        boolean stopOnNonPass = params.getStopOnNonPass() == null || Boolean.TRUE.equals(params.getStopOnNonPass());
        long perRunTimeoutMs = resolvePerRunTimeoutMs(params.getPerRunTimeoutMs());
        String triggerType = StrUtil.blankToDefault(params.getTriggerType(), "suite");

        TriggerTestFlowRunBatchResult result = TriggerTestFlowRunBatchResult.builder()
                .items(new ArrayList<>())
                .build();

        boolean stoppedEarly = false;
        for (int i = 0; i < queue.size(); i++) {
            if (stoppedEarly) {
                TriggerTestFlowRunBatchItemParams skipped = queue.get(i);
                result.getItems().add(TriggerTestFlowRunBatchItemResult.builder()
                        .testFlowId(skipped.getTestFlowId())
                        .runScenarioId(blankToNull(skipped.getRunScenarioId()))
                        .status(RunStatus.SKIPPED.getCode())
                        .errorMessage("队列已提前停止")
                        .build());
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            TriggerTestFlowRunBatchItemParams item = queue.get(i);
            TriggerTestFlowRunBatchItemResult itemResult = runBatchItem(
                    item, params.getTestProjectEnvId(), params.getOperatorUserId(), triggerType, perRunTimeoutMs);
            result.getItems().add(itemResult);
            tallyBatchItem(result, itemResult.getStatus());

            if (stopOnNonPass && !RunStatus.PASSED.matches(itemResult.getStatus())) {
                stoppedEarly = true;
            }
        }
        result.setStoppedEarly(stoppedEarly);
        return result;
    }

    /**
     * 解析批量队列：优先 items；否则由 testFlowIds 去重保序展开。
     */
    private List<TriggerTestFlowRunBatchItemParams> resolveBatchQueue(TriggerTestFlowRunBatchParams params) {
        List<TriggerTestFlowRunBatchItemParams> fromItems = params.getItems();
        if (fromItems != null && !fromItems.isEmpty()) {
            List<TriggerTestFlowRunBatchItemParams> queue = new ArrayList<>();
            for (TriggerTestFlowRunBatchItemParams item : fromItems) {
                if (item == null || item.getTestFlowId() == null) {
                    throw new ServiceException("批量跑流项缺少 testFlowId");
                }
                queue.add(item);
            }
            return queue;
        }
        List<Long> flowIds = params.getTestFlowIds();
        if (flowIds == null || flowIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (Long id : flowIds) {
            if (id != null) {
                ordered.add(id);
            }
        }
        List<TriggerTestFlowRunBatchItemParams> queue = new ArrayList<>();
        for (Long id : ordered) {
            queue.add(TriggerTestFlowRunBatchItemParams.builder().testFlowId(id).build());
        }
        return queue;
    }

    private long resolvePerRunTimeoutMs(Long requested) {
        long value = requested == null ? DEFAULT_PER_RUN_TIMEOUT_MS : requested;
        if (value < 1L) {
            value = DEFAULT_PER_RUN_TIMEOUT_MS;
        }
        return Math.min(value, MAX_PER_RUN_TIMEOUT_MS);
    }

    /**
     * 触发单条并等待终态；触发异常记入 item，不向外抛。
     */
    private TriggerTestFlowRunBatchItemResult runBatchItem(
            TriggerTestFlowRunBatchItemParams item,
            Long testProjectEnvId,
            Long operatorUserId,
            String triggerType,
            long perRunTimeoutMs) {
        TriggerTestFlowRunBatchItemResult.TriggerTestFlowRunBatchItemResultBuilder builder =
                TriggerTestFlowRunBatchItemResult.builder()
                        .testFlowId(item.getTestFlowId())
                        .runScenarioId(blankToNull(item.getRunScenarioId()));
        try {
            TriggerTestFlowRunOutcome outcome = triggerRun(TriggerTestFlowRunParams.builder()
                    .testFlowId(item.getTestFlowId())
                    .runScenarioId(blankToNull(item.getRunScenarioId()))
                    .testProjectEnvId(testProjectEnvId)
                    .triggerType(triggerType)
                    .operatorUserId(operatorUserId)
                    .build());
            Long runId = resolveStartedRunId(outcome, perRunTimeoutMs);
            if (runId == null) {
                return builder.status(RunStatus.FAILED.getCode())
                        .errorMessage(outcome != null && StrUtil.isNotBlank(outcome.getMessage())
                                ? outcome.getMessage()
                                : "排队取消或超时未开跑")
                        .build();
            }
            builder.runId(runId);
            TestFlowRunResult terminal = awaitRunTerminal(runId, perRunTimeoutMs);
            if (terminal == null) {
                return builder.status(RunStatus.FAILED.getCode())
                        .errorMessage("运行记录不存在")
                        .build();
            }
            return builder
                    .status(terminal.getStatus())
                    .durationMs(terminal.getDurationMs())
                    .errorMessage(terminal.getErrorMessage())
                    .runScenarioId(blankToNull(StrUtil.blankToDefault(
                            terminal.getRunScenarioId(), item.getRunScenarioId())))
                    .build();
        } catch (Exception e) {
            return builder
                    .status(RunStatus.FAILED.getCode())
                    .errorMessage(StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()))
                    .build();
        }
    }

    private void tallyBatchItem(TriggerTestFlowRunBatchResult result, String status) {
        if (RunStatus.PASSED.matches(status)) {
            result.setPassedCount(result.getPassedCount() + 1);
        } else if (RunStatus.FAILED.matches(status)) {
            result.setFailedCount(result.getFailedCount() + 1);
        } else if (RunStatus.PAUSED.matches(status)) {
            result.setPausedCount(result.getPausedCount() + 1);
        } else {
            result.setOtherCount(result.getOtherCount() + 1);
        }
    }

    private static String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 短间隔查库，直到 status 不再是 running，或超过 timeoutMs。
     *
     * @param timeoutMs 最长等待；超时仍返回当前快照（可能仍为 running）
     */
    @Override
    public TestFlowRunResult awaitRunTerminal(Long testFlowRunId, long timeoutMs) {
        if (testFlowRunId == null) {
            throw new ServiceException("testFlowRunId 不能为空");
        }
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        TestFlowRunResult latest = null;
        while (System.currentTimeMillis() <= deadline) {
            latest = testFlowRunService.selectTestFlowRunResult(testFlowRunId);
            if (latest == null) {
                return null;
            }
            String status = latest.getStatus();
            if (status != null && !RunStatus.RUNNING.matches(status)) {
                return latest;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return latest;
            }
        }
        return latest != null ? latest : testFlowRunService.selectTestFlowRunResult(testFlowRunId);
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
            testProjectMemberService.requireCapability(
                    testFlow.getTestProjectId(), TestProjectMemberPerm.FLOW_VIEW);
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
            testProjectMemberService.requireCapability(
                    testFlow.getTestProjectId(), TestProjectMemberPerm.FLOW_RUN);
        }

        TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(run.getTestProjectEnvId());
        if (env == null || (env.getDelStatus() != null && env.getDelStatus() != 0)) {
            throw new ServiceException("测试环境不存在");
        }

        ResumeDecision decision = ResumeDecision.builder()
                .decision(params != null ? params.getDecision() : null)
                .snapshotId(params != null ? params.getSnapshotId() : null)
                .inputs(params != null ? params.getInputs() : null)
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
     * 组装 paused Run 的 pauseInfo。
     * await_input：决策仅 continueWithInput / abort，并填入节点 prompt 与 fields；
     * 其它暂停：四决策（还原重试 / 原地重试 / 跳过 / 中止）。
     * 非 paused 返回 null。
     */
    private RunPauseInfo buildPauseInfo(TestFlowRunResult run) {
        if (run == null || !RunStatus.PAUSED.matches(run.getStatus())) {
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
        RunPauseInfo.RunPauseInfoBuilder builder = RunPauseInfo.builder()
                .pauseReason(state.getPauseReason())
                .pauseNodeId(state.getPauseNodeId())
                .currentNodeId(state.getCurrentNodeId())
                .snapshotStack(stackItems)
                .pausedAt(run.getPausedAt());

        if (RunExecutionState.PAUSE_REASON_AWAIT_INPUT.equals(state.getPauseReason())) {
            builder.availableDecisions(Arrays.asList(
                    ResumeDecision.CONTINUE_WITH_INPUT,
                    ResumeDecision.ABORT
            ));
            fillAwaitInputFields(builder, run, state.getPauseNodeId());
        } else {
            builder.availableDecisions(Arrays.asList(
                    ResumeDecision.RESTORE_AND_RETRY,
                    ResumeDecision.RETRY_IN_PLACE,
                    ResumeDecision.SKIP,
                    ResumeDecision.ABORT
            ));
        }
        return builder.build();
    }

    /**
     * 若已开跑直接返回 runId；若排队则轮询绑定结果直至超时。
     */
    private Long resolveStartedRunId(TriggerTestFlowRunOutcome outcome, long timeoutMs) {
        if (outcome == null) {
            return null;
        }
        if (outcome.isStarted() && outcome.getRunId() != null) {
            return outcome.getRunId();
        }
        if (!outcome.isQueued() || StrUtil.isBlank(outcome.getQueueItemId())) {
            return outcome.getRunId();
        }
        return projectRunHubService.awaitQueuedRunStart(outcome.getQueueItemId(), timeoutMs);
    }

    /**
     * 从 Run 的 graph 快照读取暂停节点 data.prompt / data.fields，写入 pauseInfo。
     * 解析失败时不影响基础 pauseInfo。
     */
    private void fillAwaitInputFields(RunPauseInfo.RunPauseInfoBuilder builder, TestFlowRunResult run, String pauseNodeId) {
        if (run == null || pauseNodeId == null || run.getGraphJsonSnapshot() == null) {
            return;
        }
        try {
            GraphJson graph = GraphJson.parse(run.getGraphJsonSnapshot());
            if (graph == null) {
                return;
            }
            var node = GraphLookupUtils.findNode(graph.getNodes(), pauseNodeId);
            if (node == null) {
                return;
            }
            java.util.Map<String, Object> data = node.getData() != null ? node.getData() : java.util.Map.of();
            Object prompt = data.get("prompt");
            if (prompt != null) {
                builder.prompt(String.valueOf(prompt));
            }
            builder.fields(InputFieldTypes.parseFields(data.get("fields")));
        } catch (Exception ignored) {
            // 解析失败时仍返回基础 pauseInfo
        }
    }
}
