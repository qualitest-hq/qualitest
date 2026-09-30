package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.HttpEgressAllowlist;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.context.EnvUrlSupport;
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectEnvMapper;
import com.qualitest.project.params.TestProjectEnvParams;
import com.qualitest.project.result.TestProjectEnvResult;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.support.PrefabricatedTemplateExtrasSupport;
import com.qualitest.project.support.TestProjectVariableEntrySupport;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 测试项目环境Service业务层处理
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Service
public class TestProjectEnvServiceImpl implements ITestProjectEnvService {
    @Autowired
    private TestProjectEnvMapper testProjectEnvMapper;

    @Autowired
    private FlowExternalChangePublisher flowExternalChangePublisher;

    @Autowired
    private HttpEgressAllowlist httpEgressAllowlist;

    private void notifyEnvsChanged(Long testProjectId) {
        if (testProjectId == null) {
            return;
        }
        flowExternalChangePublisher.publishProjectEnvsChanged(
                testProjectId, FlowExternalChangeSourceHolder.getOrDefault());
    }

    /**
     * 查询测试项目环境列表
     *
     * @param testProjectEnv 测试项目环境
     * @return 测试项目环境
     */
    @Override
    public List<TestProjectEnv> selectTestProjectEnvList(TestProjectEnv testProjectEnv) {
        return testProjectEnvMapper.selectTestProjectEnvList(testProjectEnv);
    }

    /**
     * 查询测试项目环境
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 测试项目环境
     */
    @Override
    public TestProjectEnv selectTestProjectEnvById(Long testProjectEnvId) {
        return testProjectEnvMapper.selectTestProjectEnvById(testProjectEnvId);
    }

    /**
     * 查询测试项目环境Result列表
     *
     * @param params 测试项目环境Params
     * @return 测试项目环境Result集合
     */
    @Override
    public List<TestProjectEnvResult> selectTestProjectEnvResultList(TestProjectEnvParams params) {
        return testProjectEnvMapper.selectTestProjectEnvResultList(params);
    }

    /**
     * 获取测试项目环境详细信息
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 测试项目环境Result
     */
    @Override
    public TestProjectEnvResult selectTestProjectEnvResult(Long testProjectEnvId) {
        return testProjectEnvMapper.selectTestProjectEnvResult(testProjectEnvId);
    }

    /**
     * 新增测试项目环境
     *
     * @param testProjectEnv 测试项目环境
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectEnv(TestProjectEnv testProjectEnv) {
        if (Objects.isNull(testProjectEnv.getTestProjectEnvId())) {
            testProjectEnv.setTestProjectEnvId(IdUtil.getSnowflakeNextId());
        }
        if (StrUtil.isBlank(testProjectEnv.getShareStatus())) {
            testProjectEnv.setShareStatus(TestProjectConstants.DEFAULT_ENV_SHARE_STATUS);
        }
        assertEnvUrlAllowed(testProjectEnv.getEnvUrl());
        normalizeEnvVariables(testProjectEnv);
        if (testProjectEnv.getSortNum() == null && testProjectEnv.getTestProjectId() != null) {
            Integer maxSort = testProjectEnvMapper.selectMaxSortNumByProjectId(testProjectEnv.getTestProjectId());
            testProjectEnv.setSortNum(maxSort == null ? 0 : maxSort + 1);
        } else if (testProjectEnv.getSortNum() == null) {
            testProjectEnv.setSortNum(0);
        }
        testProjectEnv.setCreateTime(DateUtils.getNowDate());
        int rows = testProjectEnvMapper.insertTestProjectEnv(testProjectEnv);
        if (rows > 0) {
            notifyEnvsChanged(testProjectEnv.getTestProjectId());
        }
        return rows;
    }

    /**
     * 修改测试项目环境
     *
     * @param testProjectEnv 测试项目环境
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectEnv(TestProjectEnv testProjectEnv) {
        assertEnvUrlAllowed(testProjectEnv.getEnvUrl());
        normalizeEnvVariables(testProjectEnv);
        testProjectEnv.setUpdateTime(DateUtils.getNowDate());
        int rows = testProjectEnvMapper.updateTestProjectEnv(testProjectEnv);
        if (rows > 0) {
            Long projectId = testProjectEnv.getTestProjectId();
            if (projectId == null && testProjectEnv.getTestProjectEnvId() != null) {
                TestProjectEnv existing = testProjectEnvMapper.selectTestProjectEnvById(
                        testProjectEnv.getTestProjectEnvId());
                if (existing != null) {
                    projectId = existing.getTestProjectId();
                }
            }
            notifyEnvsChanged(projectId);
        }
        return rows;
    }

    /**
     * 校验环境 URL 是否允许出站。
     * <p>
     * 空串与建项占位地址不校验（便于新建项目）；纯字符串与多模块 JSON 中的每个基址都会检查。
     * 白名单关闭时只校验协议与主机；开启时再核对 host:port。
     *
     * @param envUrl 环境 URL 原始值；null 表示本次未改该字段，跳过
     */
    private void assertEnvUrlAllowed(String envUrl) {
        if (envUrl == null || PrefabricatedTemplateExtrasSupport.isPlaceholderEnvUrl(envUrl)) {
            return;
        }
        String raw = envUrl.trim();
        if (raw.startsWith("{")) {
            JSONObject modules;
            try {
                modules = JSON.parseObject(raw);
            } catch (Exception e) {
                throw new ServiceException("环境 URL 格式无效");
            }
            if (modules == null || modules.isEmpty()) {
                return;
            }
            for (Object value : modules.values()) {
                if (!(value instanceof String s) || s.isBlank()) {
                    continue;
                }
                assertSingleEnvBaseUrlAllowed(s.trim());
            }
            return;
        }
        assertSingleEnvBaseUrlAllowed(raw);
    }

    /**
     * 校验单个基址 URL（可无协议，保存前会补 http://）。
     *
     * @param baseUrl 单个模块或环境的基址
     */
    private void assertSingleEnvBaseUrlAllowed(String baseUrl) {
        String url = EnvUrlSupport.ensureHttpSchemeForRequest(baseUrl);
        if (url.isEmpty()) {
            return;
        }
        String err = httpEgressAllowlist.check(url);
        if (err != null) {
            throw new ServiceException(err);
        }
    }

    /**
     * 将 env_values 规范为变量条目 JSON 数组并校验。
     */
    private void normalizeEnvVariables(TestProjectEnv testProjectEnv) {
        String raw = testProjectEnv.getEnvVariables();
        if (StrUtil.isBlank(raw)) {
            testProjectEnv.setEnvVariables(TestProjectConstants.EMPTY_ENV_VARIABLES_JSON);
            return;
        }
        testProjectEnv.setEnvVariables(TestProjectVariableEntrySupport.normalizeVariablesJsonForPersist(raw));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int reorderTestProjectEnvs(Long testProjectId, List<Long> orderedEnvIds, Long userId) {
        if (testProjectId == null || userId == null) {
            throw new ServiceException("参数不完整");
        }
        if (orderedEnvIds == null || orderedEnvIds.isEmpty()) {
            return 0;
        }
        if (orderedEnvIds.size() != new HashSet<>(orderedEnvIds).size()) {
            throw new ServiceException("排序列表包含重复项");
        }
        List<Long> dbIds = testProjectEnvMapper.selectEnvIdsByProjectAndUser(testProjectId, userId);
        if (dbIds.size() != orderedEnvIds.size()) {
            throw new ServiceException("环境与列表不一致，请刷新后重试");
        }
        Set<Long> dbSet = new HashSet<>(dbIds);
        for (Long id : orderedEnvIds) {
            if (!dbSet.contains(id)) {
                throw new ServiceException("环境与列表不一致，请刷新后重试");
            }
        }
        List<TestProjectEnv> sortRows = new ArrayList<>();
        for (int i = 0; i < orderedEnvIds.size(); i++) {
            TestProjectEnv row = new TestProjectEnv();
            row.setTestProjectEnvId(orderedEnvIds.get(i));
            row.setSortNum(i);
            sortRows.add(row);
        }
        Date now = DateUtils.getNowDate();
        int updated = testProjectEnvMapper.batchUpdateSortNumForReorder(testProjectId, userId, now, sortRows);
        if (updated > 0) {
            notifyEnvsChanged(testProjectId);
        }
        return updated;
    }

    /**
     * 批量删除测试项目环境
     *
     * @param testProjectEnvIdList 需要删除的测试项目环境主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectEnvByIdList(List<Long> testProjectEnvIdList) {
        return testProjectEnvMapper.deleteTestProjectEnvByIdList(testProjectEnvIdList);
    }

    /**
     * 删除测试项目环境信息
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectEnvById(Long testProjectEnvId) {
        return testProjectEnvMapper.deleteTestProjectEnvById(testProjectEnvId);
    }

    /**
     * 逻辑删除测试项目环境信息
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectEnvById(Long testProjectEnvId) {
        Long projectId = null;
        if (testProjectEnvId != null) {
            TestProjectEnv existing = testProjectEnvMapper.selectTestProjectEnvById(testProjectEnvId);
            if (existing != null) {
                projectId = existing.getTestProjectId();
            }
        }
        int rows = testProjectEnvMapper.logicDeleteTestProjectEnvById(testProjectEnvId);
        if (rows > 0) {
            notifyEnvsChanged(projectId);
        }
        return rows;
    }

    /**
     * 批量逻辑删除测试项目环境信息
     *
     * @param testProjectEnvIdList 测试项目环境主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectEnvByIdList(List<Long> testProjectEnvIdList) {
        Long projectId = null;
        if (testProjectEnvIdList != null && !testProjectEnvIdList.isEmpty()) {
            TestProjectEnv existing = testProjectEnvMapper.selectTestProjectEnvById(testProjectEnvIdList.get(0));
            if (existing != null) {
                projectId = existing.getTestProjectId();
            }
        }
        int rows = testProjectEnvMapper.logicDeleteTestProjectEnvByIdList(testProjectEnvIdList);
        if (rows > 0) {
            notifyEnvsChanged(projectId);
        }
        return rows;
    }

    /**
     * 查询测试项目环境数量
     *
     * @param params 测试项目环境Params
     * @return 数量
     */
    @Override
    public int selectTestProjectEnvCount(TestProjectEnvParams params) {
        return testProjectEnvMapper.selectTestProjectEnvCount(params);
    }

    /**
     * 按条件查询单条测试项目环境
     *
     * @param params 测试项目环境Params
     * @return 测试项目环境
     */
    @Override
    public TestProjectEnv selectTestProjectEnvOne(TestProjectEnvParams params) {
        return testProjectEnvMapper.selectTestProjectEnvOne(params);
    }
}
