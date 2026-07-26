package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.project.mapper.TestFlowRunMapper;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.params.TestFlowRunParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowRunService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试流运行Service业务层处理
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Service
public class TestFlowRunServiceImpl implements ITestFlowRunService {
    @Autowired
    private TestFlowRunMapper testFlowRunMapper;

    /**
     * 查询测试流运行列表
     *
     * @param testFlowRun 测试流运行
     * @return 测试流运行
     */
    @Override
    public List<TestFlowRun> selectTestFlowRunList(TestFlowRun testFlowRun) {
        return testFlowRunMapper.selectTestFlowRunList(testFlowRun);
    }

    /**
     * 查询测试流运行
     *
     * @param testFlowRunId 测试流运行主键
     * @return 测试流运行
     */
    @Override
    public TestFlowRun selectTestFlowRunById(Long testFlowRunId) {
        return testFlowRunMapper.selectTestFlowRunById(testFlowRunId);
    }

    /**
     * 查询测试流运行Result列表
     *
     * @param params 测试流运行Params
     * @return 测试流运行Result集合
     */
    @Override
    public List<TestFlowRunResult> selectTestFlowRunResultList(TestFlowRunParams params) {
        return testFlowRunMapper.selectTestFlowRunResultList(params);
    }

    /**
     * 获取测试流运行详细信息
     *
     * @param testFlowRunId 测试流运行主键
     * @return 测试流运行Result
     */
    @Override
    public TestFlowRunResult selectTestFlowRunResult(Long testFlowRunId) {
        return testFlowRunMapper.selectTestFlowRunResult(testFlowRunId);
    }

    /**
     * 新增测试流运行
     *
     * @param testFlowRun 测试流运行
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestFlowRun(TestFlowRun testFlowRun) {
        if (Objects.isNull(testFlowRun.getTestFlowRunId())) {
            testFlowRun.setTestFlowRunId(IdUtil.getSnowflakeNextId());
        }
        testFlowRun.setCreateTime(DateUtils.getNowDate());
        return testFlowRunMapper.insertTestFlowRun(testFlowRun);
    }

    /**
     * 修改测试流运行
     *
     * @param testFlowRun 测试流运行
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestFlowRun(TestFlowRun testFlowRun) {
        testFlowRun.setUpdateTime(DateUtils.getNowDate());
        return testFlowRunMapper.updateTestFlowRun(testFlowRun);
    }

    @Override
    public int updateStatusFromPausedToRunning(Long testFlowRunId) {
        return testFlowRunMapper.updateStatusFromPausedToRunning(testFlowRunId, DateUtils.getNowDate());
    }

    /**
     * 批量删除测试流运行
     * 
     * @param testFlowRunIdList 需要删除的测试流运行主键集合
     * @return 结果
     */
    @Override
    public int deleteTestFlowRunByIdList(List<Long> testFlowRunIdList) {
        return testFlowRunMapper.deleteTestFlowRunByIdList(testFlowRunIdList);
    }

    /**
     * 删除测试流运行信息
     * 
     * @param testFlowRunId 测试流运行主键
     * @return 结果
     */
    @Override
    public int deleteTestFlowRunById(Long testFlowRunId) {
        return testFlowRunMapper.deleteTestFlowRunById(testFlowRunId);
    }

    /**
     * 逻辑删除测试流运行信息
     * 
     * @param testFlowRunId 测试流运行主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestFlowRunById(Long testFlowRunId) {
        return testFlowRunMapper.logicDeleteTestFlowRunById(testFlowRunId);
    }

    /**
     * 批量逻辑删除测试流运行信息
     * 
     * @param testFlowRunIdList 测试流运行主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestFlowRunByIdList(List<Long> testFlowRunIdList) {
        return testFlowRunMapper.logicDeleteTestFlowRunByIdList(testFlowRunIdList);
    }

    /**
     * 查询测试流运行数量
     *
     * @param params 测试流运行Params
     * @return 数量
     */
    @Override
    public int selectTestFlowRunCount(TestFlowRunParams params) {
        return testFlowRunMapper.selectTestFlowRunCount(params);
    }

    /**
     * 按条件查询单条测试流运行
     *
     * @param params 测试流运行Params
     * @return 测试流运行
     */
    @Override
    public TestFlowRun selectTestFlowRunOne(TestFlowRunParams params) {
        return testFlowRunMapper.selectTestFlowRunOne(params);
    }
}
