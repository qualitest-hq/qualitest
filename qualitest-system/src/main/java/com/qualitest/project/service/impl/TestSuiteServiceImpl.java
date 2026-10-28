package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestSuite;
import com.qualitest.project.domain.TestSuiteItem;
import com.qualitest.project.mapper.TestSuiteItemMapper;
import com.qualitest.project.mapper.TestSuiteMapper;
import com.qualitest.project.params.TestSuiteItemParams;
import com.qualitest.project.params.TestSuiteItemSaveParams;
import com.qualitest.project.params.TestSuiteParams;
import com.qualitest.project.params.TestSuiteSaveParams;
import com.qualitest.project.result.TestSuiteItemResult;
import com.qualitest.project.result.TestSuiteResult;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 测试套件Service业务层处理
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Service
@RequiredArgsConstructor
public class TestSuiteServiceImpl implements ITestSuiteService {

    private final TestSuiteMapper testSuiteMapper;
    private final TestSuiteItemMapper testSuiteItemMapper;
    private final ITestFlowService testFlowService;

    /**
     * 查询测试套件列表
     *
     * @param testSuite 测试套件
     * @return 测试套件
     */
    @Override
    public List<TestSuite> selectTestSuiteList(TestSuite testSuite) {
        return testSuiteMapper.selectTestSuiteList(testSuite);
    }

    /**
     * 查询测试套件
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件
     */
    @Override
    public TestSuite selectTestSuiteById(Long testSuiteId) {
        return testSuiteMapper.selectTestSuiteById(testSuiteId);
    }

    /**
     * 查询测试套件Result列表
     *
     * @param params 测试套件Params
     * @return 测试套件Result集合
     */
    @Override
    public List<TestSuiteResult> selectTestSuiteResultList(TestSuiteParams params) {
        return testSuiteMapper.selectTestSuiteResultList(params);
    }

    /**
     * 获取测试套件详细信息（头信息，不含项）
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件Result
     */
    @Override
    public TestSuiteResult selectTestSuiteResult(Long testSuiteId) {
        return testSuiteMapper.selectTestSuiteResult(testSuiteId);
    }

    /**
     * 获取测试套件详情（含项列表）
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件Result
     */
    @Override
    public TestSuiteResult selectTestSuiteDetail(Long testSuiteId) {
        TestSuiteResult result = testSuiteMapper.selectTestSuiteResult(testSuiteId);
        if (result == null) {
            return null;
        }
        List<TestSuiteItemResult> items = testSuiteItemMapper.selectTestSuiteItemResultList(
                TestSuiteItemParams.builder().testSuiteId(testSuiteId).build());
        result.setItems(items);
        return result;
    }

    /**
     * 新建或更新套件（项整表替换）
     *
     * @param params 保存参数
     * @return 套件ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTestSuite(TestSuiteSaveParams params) {
        if (params == null || params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        String name = StrUtil.trim(params.getSuiteName());
        if (StrUtil.isBlank(name)) {
            throw new ServiceException("套件名称不能为空");
        }
        List<TestSuiteItemSaveParams> items = params.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("套件至少包含一项");
        }
        int stopOnNonPass = params.getStopOnNonPass() == null || Boolean.TRUE.equals(params.getStopOnNonPass())
                ? 1 : 0;
        Date now = DateUtils.getNowDate();

        Long suiteId = params.getTestSuiteId();
        if (suiteId == null) {
            suiteId = IdUtil.getSnowflakeNextId();
            TestSuite suite = TestSuite.builder()
                    .testSuiteId(suiteId)
                    .testProjectId(params.getTestProjectId())
                    .suiteName(name)
                    .suiteDescription(StrUtil.trim(params.getSuiteDescription()))
                    .stopOnNonPass(stopOnNonPass)
                    .delStatus(0)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            testSuiteMapper.insertTestSuite(suite);
        } else {
            TestSuite existing = testSuiteMapper.selectTestSuiteById(suiteId);
            if (existing == null || (existing.getDelStatus() != null && existing.getDelStatus() != 0)) {
                throw new ServiceException("套件不存在");
            }
            if (!params.getTestProjectId().equals(existing.getTestProjectId())) {
                throw new ServiceException("套件不属于该项目");
            }
            TestSuite patch = TestSuite.builder()
                    .testSuiteId(suiteId)
                    .suiteName(name)
                    .suiteDescription(StrUtil.trim(params.getSuiteDescription()))
                    .stopOnNonPass(stopOnNonPass)
                    .updateTime(now)
                    .build();
            testSuiteMapper.updateTestSuite(patch);
            testSuiteItemMapper.logicDeleteBySuiteId(suiteId);
        }

        int sort = 0;
        for (TestSuiteItemSaveParams item : items) {
            if (item == null || item.getTestFlowId() == null) {
                throw new ServiceException("套件项缺少 testFlowId");
            }
            TestFlow flow = testFlowService.selectTestFlowById(item.getTestFlowId());
            if (flow == null || (flow.getDelStatus() != null && flow.getDelStatus() != 0)) {
                throw new ServiceException("测试流不存在：" + item.getTestFlowId());
            }
            if (!params.getTestProjectId().equals(flow.getTestProjectId())) {
                throw new ServiceException("测试流不属于该项目：" + item.getTestFlowId());
            }
            int sortNum = item.getSortNum() != null ? item.getSortNum() : sort;
            String scenarioId = StrUtil.trim(item.getRunScenarioId());
            if (StrUtil.isBlank(scenarioId)) {
                scenarioId = null;
            }
            TestSuiteItem row = TestSuiteItem.builder()
                    .testSuiteItemId(IdUtil.getSnowflakeNextId())
                    .testSuiteId(suiteId)
                    .testFlowId(item.getTestFlowId())
                    .runScenarioId(scenarioId)
                    .sortNum(sortNum)
                    .delStatus(0)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            testSuiteItemMapper.insertTestSuiteItem(row);
            sort++;
        }
        return suiteId;
    }

    /**
     * 新增测试套件
     *
     * @param testSuite 测试套件
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestSuite(TestSuite testSuite) {
        if (Objects.isNull(testSuite.getTestSuiteId())) {
            testSuite.setTestSuiteId(IdUtil.getSnowflakeNextId());
        }
        if (testSuite.getDelStatus() == null) {
            testSuite.setDelStatus(0);
        }
        testSuite.setCreateTime(DateUtils.getNowDate());
        return testSuiteMapper.insertTestSuite(testSuite);
    }

    /**
     * 修改测试套件
     *
     * @param testSuite 测试套件
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestSuite(TestSuite testSuite) {
        testSuite.setUpdateTime(DateUtils.getNowDate());
        return testSuiteMapper.updateTestSuite(testSuite);
    }

    /**
     * 批量删除测试套件
     *
     * @param testSuiteIdList 需要删除的测试套件主键集合
     * @return 结果
     */
    @Override
    public int deleteTestSuiteByIdList(List<Long> testSuiteIdList) {
        return testSuiteMapper.deleteTestSuiteByIdList(testSuiteIdList);
    }

    /**
     * 删除测试套件信息
     *
     * @param testSuiteId 测试套件主键
     * @return 结果
     */
    @Override
    public int deleteTestSuiteById(Long testSuiteId) {
        return testSuiteMapper.deleteTestSuiteById(testSuiteId);
    }

    /**
     * 逻辑删除测试套件信息
     *
     * @param testSuiteId 测试套件主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestSuiteById(Long testSuiteId) {
        testSuiteItemMapper.logicDeleteBySuiteId(testSuiteId);
        return testSuiteMapper.logicDeleteTestSuiteById(testSuiteId);
    }

    /**
     * 批量逻辑删除测试套件信息（同时逻辑删除项）
     *
     * @param testSuiteIdList 测试套件主键集合
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestSuiteByIdList(List<Long> testSuiteIdList) {
        if (testSuiteIdList == null || testSuiteIdList.isEmpty()) {
            return 0;
        }
        testSuiteItemMapper.logicDeleteBySuiteIdList(testSuiteIdList);
        return testSuiteMapper.logicDeleteTestSuiteByIdList(testSuiteIdList);
    }

    /**
     * 查询测试套件数量
     *
     * @param params 测试套件Params
     * @return 数量
     */
    @Override
    public int selectTestSuiteCount(TestSuiteParams params) {
        return testSuiteMapper.selectTestSuiteCount(params);
    }

    /**
     * 按条件查询单条测试套件
     *
     * @param params 测试套件Params
     * @return 测试套件
     */
    @Override
    public TestSuite selectTestSuiteOne(TestSuiteParams params) {
        return testSuiteMapper.selectTestSuiteOne(params);
    }
}
