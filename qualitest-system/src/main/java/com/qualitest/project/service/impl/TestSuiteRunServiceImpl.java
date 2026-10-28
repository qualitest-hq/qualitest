package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestSuiteRun;
import com.qualitest.project.domain.TestSuiteRunItem;
import com.qualitest.project.mapper.TestSuiteRunItemMapper;
import com.qualitest.project.mapper.TestSuiteRunMapper;
import com.qualitest.project.params.BindTestSuiteRunItemParams;
import com.qualitest.project.params.FinishTestSuiteRunItemParams;
import com.qualitest.project.params.StartTestSuiteRunParams;
import com.qualitest.project.params.TestSuiteRunItemParams;
import com.qualitest.project.params.TestSuiteRunParams;
import com.qualitest.project.result.TestSuiteItemResult;
import com.qualitest.project.result.TestSuiteResult;
import com.qualitest.project.result.TestSuiteRunItemResult;
import com.qualitest.project.result.TestSuiteRunResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestSuiteRunService;
import com.qualitest.project.service.ITestSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 测试套件运行Service业务层处理
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Service
@RequiredArgsConstructor
public class TestSuiteRunServiceImpl implements ITestSuiteRunService {

    private final TestSuiteRunMapper testSuiteRunMapper;
    private final TestSuiteRunItemMapper testSuiteRunItemMapper;
    private final ITestSuiteService testSuiteService;
    private final ITestFlowService testFlowService;

    /**
     * 查询测试套件运行列表
     *
     * @param testSuiteRun 测试套件运行
     * @return 测试套件运行
     */
    @Override
    public List<TestSuiteRun> selectTestSuiteRunList(TestSuiteRun testSuiteRun) {
        return testSuiteRunMapper.selectTestSuiteRunList(testSuiteRun);
    }

    /**
     * 查询测试套件运行
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 测试套件运行
     */
    @Override
    public TestSuiteRun selectTestSuiteRunById(Long testSuiteRunId) {
        return testSuiteRunMapper.selectTestSuiteRunById(testSuiteRunId);
    }

    /**
     * 查询测试套件运行Result列表
     *
     * @param params 测试套件运行Params
     * @return 测试套件运行Result集合
     */
    @Override
    public List<TestSuiteRunResult> selectTestSuiteRunResultList(TestSuiteRunParams params) {
        return testSuiteRunMapper.selectTestSuiteRunResultList(params);
    }

    /**
     * 获取测试套件运行详细信息（含项）
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 测试套件运行Result
     */
    @Override
    public TestSuiteRunResult selectTestSuiteRunResult(Long testSuiteRunId) {
        TestSuiteRunResult result = testSuiteRunMapper.selectTestSuiteRunResult(testSuiteRunId);
        if (result == null) {
            return null;
        }
        result.setItems(testSuiteRunItemMapper.selectTestSuiteRunItemResultList(
                TestSuiteRunItemParams.builder().testSuiteRunId(testSuiteRunId).build()));
        return result;
    }

    /**
     * 新增测试套件运行
     *
     * @param testSuiteRun 测试套件运行
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestSuiteRun(TestSuiteRun testSuiteRun) {
        if (Objects.isNull(testSuiteRun.getTestSuiteRunId())) {
            testSuiteRun.setTestSuiteRunId(IdUtil.getSnowflakeNextId());
        }
        if (testSuiteRun.getDelStatus() == null) {
            testSuiteRun.setDelStatus(0);
        }
        testSuiteRun.setCreateTime(DateUtils.getNowDate());
        return testSuiteRunMapper.insertTestSuiteRun(testSuiteRun);
    }

    /**
     * 修改测试套件运行
     *
     * @param testSuiteRun 测试套件运行
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestSuiteRun(TestSuiteRun testSuiteRun) {
        testSuiteRun.setUpdateTime(DateUtils.getNowDate());
        return testSuiteRunMapper.updateTestSuiteRun(testSuiteRun);
    }

    /**
     * 批量删除测试套件运行
     *
     * @param testSuiteRunIdList 需要删除的测试套件运行主键集合
     * @return 结果
     */
    @Override
    public int deleteTestSuiteRunByIdList(List<Long> testSuiteRunIdList) {
        return testSuiteRunMapper.deleteTestSuiteRunByIdList(testSuiteRunIdList);
    }

    /**
     * 删除测试套件运行信息
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 结果
     */
    @Override
    public int deleteTestSuiteRunById(Long testSuiteRunId) {
        return testSuiteRunMapper.deleteTestSuiteRunById(testSuiteRunId);
    }

    /**
     * 逻辑删除测试套件运行信息
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestSuiteRunById(Long testSuiteRunId) {
        return testSuiteRunMapper.logicDeleteTestSuiteRunById(testSuiteRunId);
    }

    /**
     * 批量逻辑删除测试套件运行信息
     *
     * @param testSuiteRunIdList 测试套件运行主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestSuiteRunByIdList(List<Long> testSuiteRunIdList) {
        return testSuiteRunMapper.logicDeleteTestSuiteRunByIdList(testSuiteRunIdList);
    }

    /**
     * 查询测试套件运行数量
     *
     * @param params 测试套件运行Params
     * @return 数量
     */
    @Override
    public int selectTestSuiteRunCount(TestSuiteRunParams params) {
        return testSuiteRunMapper.selectTestSuiteRunCount(params);
    }

    /**
     * 按条件查询单条测试套件运行
     *
     * @param params 测试套件运行Params
     * @return 测试套件运行
     */
    @Override
    public TestSuiteRun selectTestSuiteRunOne(TestSuiteRunParams params) {
        return testSuiteRunMapper.selectTestSuiteRunOne(params);
    }

    /**
     * 开始套件运行：按已保存套件写入 running 头与 pending 项，立刻返回；由客户端串行 trigger 并回写
     *
     * @param params 启动参数（须含 testSuiteId）
     * @return 套件运行详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestSuiteRunResult start(StartTestSuiteRunParams params) {
        if (params == null || params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        if (params.getTestSuiteId() == null) {
            throw new ServiceException("testSuiteId 不能为空");
        }

        TestSuiteResult suite = testSuiteService.selectTestSuiteDetail(params.getTestSuiteId());
        if (suite == null) {
            throw new ServiceException("套件不存在");
        }
        if (!params.getTestProjectId().equals(suite.getTestProjectId())) {
            throw new ServiceException("套件不属于该项目");
        }
        List<TestSuiteItemResult> suiteItems = suite.getItems();
        if (suiteItems == null || suiteItems.isEmpty()) {
            throw new ServiceException("运行队列不能为空");
        }

        String nameSnapshot = StrUtil.trim(params.getSuiteNameSnapshot());
        if (StrUtil.isBlank(nameSnapshot)) {
            nameSnapshot = suite.getSuiteName();
        }
        boolean stopOnNonPass;
        if (params.getStopOnNonPass() != null) {
            stopOnNonPass = Boolean.TRUE.equals(params.getStopOnNonPass());
        } else {
            stopOnNonPass = suite.getStopOnNonPass() == null || suite.getStopOnNonPass() == 1;
        }

        Date now = DateUtils.getNowDate();
        Long runId = IdUtil.getSnowflakeNextId();
        Long operatorId = null;
        try {
            operatorId = SecurityUtils.getUserId();
        } catch (Exception ignored) {
            // 无登录态时允许空操作者
        }

        TestSuiteRun run = TestSuiteRun.builder()
                .testSuiteRunId(runId)
                .testProjectId(params.getTestProjectId())
                .testSuiteId(suite.getTestSuiteId())
                .suiteNameSnapshot(nameSnapshot)
                .status(RunStatus.RUNNING.getCode())
                .stopOnNonPass(stopOnNonPass ? 1 : 0)
                .testProjectEnvId(params.getTestProjectEnvId())
                .triggerType("suite")
                .passedCount(0)
                .failedCount(0)
                .pausedCount(0)
                .skippedCount(0)
                .otherCount(0)
                .startedAt(now)
                .operatorUserId(operatorId)
                .delStatus(0)
                .createTime(now)
                .updateTime(now)
                .build();
        testSuiteRunMapper.insertTestSuiteRun(run);

        int sort = 0;
        for (TestSuiteItemResult item : suiteItems) {
            if (item == null || item.getTestFlowId() == null) {
                throw new ServiceException("运行项缺少 testFlowId");
            }
            TestFlow flow = testFlowService.selectTestFlowById(item.getTestFlowId());
            if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
                throw new ServiceException("测试流不存在：" + item.getTestFlowId());
            }
            if (!params.getTestProjectId().equals(flow.getTestProjectId())) {
                throw new ServiceException("测试流不属于该项目：" + item.getTestFlowId());
            }
            String flowName = StrUtil.blankToDefault(StrUtil.trim(item.getFlowName()), flow.getFlowName());
            String scenarioId = blankToNull(item.getRunScenarioId());
            String scenarioName = resolveScenarioName(flow, scenarioId);
            int sortNum = item.getSortNum() != null ? item.getSortNum() : sort;
            TestSuiteRunItem row = TestSuiteRunItem.builder()
                    .testSuiteRunItemId(IdUtil.getSnowflakeNextId())
                    .testSuiteRunId(runId)
                    .sortNum(sortNum)
                    .testFlowId(item.getTestFlowId())
                    .flowNameSnapshot(flowName)
                    .runScenarioId(scenarioId)
                    .scenarioNameSnapshot(scenarioName)
                    .status("pending")
                    .delStatus(0)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            testSuiteRunItemMapper.insertTestSuiteRunItem(row);
            sort++;
        }
        return selectTestSuiteRunResult(runId);
    }

    /**
     * 运行项绑定正式 RunId，状态置为 running
     *
     * @param testSuiteRunId     套件运行ID
     * @param testSuiteRunItemId 套件运行项ID
     * @param params             绑定参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindRun(Long testSuiteRunId, Long testSuiteRunItemId, BindTestSuiteRunItemParams params) {
        TestSuiteRun run = requireRunning(testSuiteRunId);
        TestSuiteRunItem item = requireItem(testSuiteRunId, testSuiteRunItemId);
        if (params == null || params.getTestFlowRunId() == null) {
            throw new ServiceException("testFlowRunId 不能为空");
        }
        Date now = DateUtils.getNowDate();
        TestSuiteRunItem patch = TestSuiteRunItem.builder()
                .testSuiteRunItemId(item.getTestSuiteRunItemId())
                .testFlowRunId(params.getTestFlowRunId())
                .status(RunStatus.RUNNING.getCode())
                .updateTime(now)
                .build();
        testSuiteRunItemMapper.updateTestSuiteRunItem(patch);
        testSuiteRunMapper.updateTestSuiteRun(TestSuiteRun.builder()
                .testSuiteRunId(run.getTestSuiteRunId())
                .updateTime(now)
                .build());
    }

    /**
     * 运行项写入终态并刷新汇总
     *
     * @param testSuiteRunId     套件运行ID
     * @param testSuiteRunItemId 套件运行项ID
     * @param params             终态参数
     * @return 刷新后的套件运行详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestSuiteRunResult finishItem(Long testSuiteRunId, Long testSuiteRunItemId,
                                         FinishTestSuiteRunItemParams params) {
        requireRunning(testSuiteRunId);
        TestSuiteRunItem item = requireItem(testSuiteRunId, testSuiteRunItemId);
        if (params == null || StrUtil.isBlank(params.getStatus())) {
            throw new ServiceException("status 不能为空");
        }
        Date now = DateUtils.getNowDate();
        TestSuiteRunItem patch = TestSuiteRunItem.builder()
                .testSuiteRunItemId(item.getTestSuiteRunItemId())
                .status(params.getStatus().trim())
                .errorMessage(params.getErrorMessage())
                .durationMs(params.getDurationMs())
                .updateTime(now)
                .build();
        testSuiteRunItemMapper.updateTestSuiteRunItem(patch);
        return refreshHeader(testSuiteRunId, false);
    }

    /**
     * 中止队列：未跑项标 skipped，头状态 aborted
     *
     * @param testSuiteRunId 套件运行ID
     * @return 刷新后的套件运行详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestSuiteRunResult abort(Long testSuiteRunId) {
        TestSuiteRun run = requireActive(testSuiteRunId);
        testSuiteRunItemMapper.markPendingSkipped(testSuiteRunId, "已中止队列");
        Date now = DateUtils.getNowDate();
        testSuiteRunMapper.updateTestSuiteRun(TestSuiteRun.builder()
                .testSuiteRunId(run.getTestSuiteRunId())
                .status("aborted")
                .finishedAt(now)
                .updateTime(now)
                .build());
        return refreshHeader(testSuiteRunId, true);
    }

    private TestSuiteRunResult refreshHeader(Long testSuiteRunId, boolean forceFinished) {
        List<TestSuiteRunItemResult> items = testSuiteRunItemMapper.selectTestSuiteRunItemResultList(
                TestSuiteRunItemParams.builder().testSuiteRunId(testSuiteRunId).build());
        int passed = 0;
        int failed = 0;
        int paused = 0;
        int skipped = 0;
        int other = 0;
        int pendingOrRunning = 0;
        for (TestSuiteRunItemResult item : items) {
            String status = item.getStatus();
            if ("pending".equals(status) || RunStatus.RUNNING.matches(status)) {
                pendingOrRunning++;
            } else if (RunStatus.PASSED.matches(status)) {
                passed++;
            } else if (RunStatus.FAILED.matches(status) || "error".equals(status)) {
                failed++;
            } else if (RunStatus.PAUSED.matches(status)) {
                paused++;
            } else if (RunStatus.SKIPPED.matches(status)) {
                skipped++;
            } else {
                other++;
            }
        }
        Date now = DateUtils.getNowDate();
        String headerStatus;
        Date finishedAt = null;
        if (forceFinished || pendingOrRunning == 0) {
            finishedAt = now;
            TestSuiteRun existing = testSuiteRunMapper.selectTestSuiteRunById(testSuiteRunId);
            if (existing != null && "aborted".equals(existing.getStatus())) {
                headerStatus = "aborted";
            } else if (failed > 0 || paused > 0 || other > 0) {
                headerStatus = RunStatus.FAILED.getCode();
            } else {
                headerStatus = RunStatus.PASSED.getCode();
            }
        } else {
            headerStatus = RunStatus.RUNNING.getCode();
        }
        testSuiteRunMapper.updateTestSuiteRun(TestSuiteRun.builder()
                .testSuiteRunId(testSuiteRunId)
                .status(headerStatus)
                .passedCount(passed)
                .failedCount(failed)
                .pausedCount(paused)
                .skippedCount(skipped)
                .otherCount(other)
                .finishedAt(finishedAt)
                .updateTime(now)
                .build());
        return selectTestSuiteRunResult(testSuiteRunId);
    }

    private TestSuiteRun requireRunning(Long testSuiteRunId) {
        TestSuiteRun run = requireActive(testSuiteRunId);
        if (!RunStatus.RUNNING.matches(run.getStatus())) {
            throw new ServiceException("套件运行已结束");
        }
        return run;
    }

    private TestSuiteRun requireActive(Long testSuiteRunId) {
        if (testSuiteRunId == null) {
            throw new ServiceException("testSuiteRunId 不能为空");
        }
        TestSuiteRun run = testSuiteRunMapper.selectTestSuiteRunById(testSuiteRunId);
        if (run == null || (run.getDelStatus() != null && run.getDelStatus() != 0)) {
            throw new ServiceException("套件运行不存在");
        }
        return run;
    }

    private TestSuiteRunItem requireItem(Long testSuiteRunId, Long testSuiteRunItemId) {
        if (testSuiteRunItemId == null) {
            throw new ServiceException("testSuiteRunItemId 不能为空");
        }
        TestSuiteRunItem item = testSuiteRunItemMapper.selectTestSuiteRunItemById(testSuiteRunItemId);
        if (item == null || (item.getDelStatus() != null && item.getDelStatus() != 0)) {
            throw new ServiceException("套件运行项不存在");
        }
        if (!testSuiteRunId.equals(item.getTestSuiteRunId())) {
            throw new ServiceException("运行项不属于该套件运行");
        }
        return item;
    }

    private static String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 从图 JSON 解析场景显示名；无场景或解析失败时回落为「默认场景」/场景 ID。
     */
    private static String resolveScenarioName(TestFlow flow, String scenarioId) {
        if (StrUtil.isBlank(scenarioId)) {
            return "默认场景";
        }
        try {
            GraphJson graph = GraphJson.parse(flow.getGraphJson());
            GraphMeta meta = graph != null ? graph.getMeta() : null;
            List<GraphRunScenario> scenarios = meta != null ? meta.getScenarios() : null;
            if (scenarios != null) {
                for (GraphRunScenario sc : scenarios) {
                    if (sc != null && scenarioId.equals(sc.getId()) && StrUtil.isNotBlank(sc.getName())) {
                        return sc.getName().trim();
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败时用场景 ID 兜底
        }
        return scenarioId;
    }
}
