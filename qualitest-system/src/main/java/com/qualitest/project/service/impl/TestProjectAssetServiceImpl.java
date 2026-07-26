package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectAsset;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.TestProjectAssetParams;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import com.qualitest.project.support.TestProjectAssetSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 项目素材库 Service 实现
 *
 * @author qualitest
 */
@Service
public class TestProjectAssetServiceImpl implements ITestProjectAssetService {

    @Autowired
    private TestProjectMapper testProjectMapper;

    /**
     * 查询项目下素材列表
     */
    @Override
    public List<TestProjectAssetResult> selectTestProjectAssetResultList(TestProjectAssetParams params) {
        Long testProjectId = requireTestProjectId(params == null ? null : params.getTestProjectId());
        List<TestProjectAsset> entries = loadEntries(testProjectId);
        return TestProjectAssetSupport.filterAndSort(entries, testProjectId, params);
    }

    /**
     * 按条目 id 查询单条
     */
    @Override
    public TestProjectAssetResult selectTestProjectAssetResult(Long testProjectId, Long id) {
        requireTestProjectId(testProjectId);
        if (id == null) {
            throw new ServiceException("请指定素材条目 id");
        }
        TestProjectAsset entry = TestProjectAssetSupport.findById(loadEntries(testProjectId), id);
        if (entry == null) {
            throw new ServiceException("素材条目不存在");
        }
        return TestProjectAssetSupport.toResult(entry, testProjectId);
    }

    /**
     * 按 key 查询单条
     */
    @Override
    public TestProjectAssetResult selectTestProjectAssetResultByKey(Long testProjectId, String key) {
        requireTestProjectId(testProjectId);
        TestProjectAsset entry = TestProjectAssetSupport.findByKey(loadEntries(testProjectId), key);
        if (entry == null) {
            throw new ServiceException("素材条目不存在");
        }
        return TestProjectAssetSupport.toResult(entry, testProjectId);
    }

    /**
     * 新增素材条目
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TestProjectAssetResult insertTestProjectAsset(TestProjectAssetSaveParams params) {
        TestProjectAssetSupport.validateSaveParams(params, false);
        Long testProjectId = params.getTestProjectId();
        List<TestProjectAsset> entries = loadEntries(testProjectId);
        TestProjectAssetSupport.assertKeyUnique(entries, params.getKey(), null);
        TestProjectAsset created = TestProjectAssetSupport.buildNewEntry(params, IdUtil.getSnowflakeNextId());
        entries.add(created);
        persistEntries(testProjectId, entries);
        return TestProjectAssetSupport.toResult(created, testProjectId);
    }

    /**
     * 修改素材条目
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TestProjectAssetResult updateTestProjectAsset(TestProjectAssetSaveParams params) {
        TestProjectAssetSupport.validateSaveParams(params, true);
        Long testProjectId = params.getTestProjectId();
        List<TestProjectAsset> entries = loadEntries(testProjectId);
        TestProjectAsset existing = TestProjectAssetSupport.findById(entries, params.getId());
        if (existing == null) {
            throw new ServiceException("素材条目不存在");
        }
        TestProjectAssetSupport.assertKeyUnique(entries, params.getKey(), params.getId());
        TestProjectAssetSupport.applyUpdate(existing, params);
        persistEntries(testProjectId, entries);
        return TestProjectAssetSupport.toResult(existing, testProjectId);
    }

    /**
     * 批量删除素材条目
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteTestProjectAssetByIds(Long testProjectId, List<Long> idList) {
        requireTestProjectId(testProjectId);
        if (idList == null || idList.isEmpty()) {
            return 0;
        }
        Set<Long> toDelete = new HashSet<>(idList);
        List<TestProjectAsset> entries = loadEntries(testProjectId);
        int before = entries.size();
        entries.removeIf(testProjectAsset -> toDelete.contains(testProjectAsset.getId()));
        int removed = before - entries.size();
        if (removed == 0) {
            throw new ServiceException("素材条目不存在");
        }
        if (removed < toDelete.size()) {
            throw new ServiceException("部分素材条目不存在或不在当前项目中");
        }
        persistEntries(testProjectId, entries);
        return removed;
    }

    /**
     * 确保项目存在
     */
    private Long requireTestProjectId(Long testProjectId) {
        if (testProjectId == null) {
            throw new ServiceException("请指定测试项目");
        }
        return testProjectId;
    }

    /**
     * 加载项目素材库
     */
    private List<TestProjectAsset> loadEntries(Long testProjectId) {
        assertProjectExists(testProjectId);
        String json = testProjectMapper.selectAssetVariablesByTestProjectId(testProjectId);
        return new ArrayList<>(TestProjectAssetSupport.parseEntries(json));
    }

    /**
     * 保存项目素材库
     */
    private void persistEntries(Long testProjectId, List<TestProjectAsset> entries) {
        TestProject row = new TestProject();
        row.setTestProjectId(testProjectId);
        row.setAssetVariables(TestProjectAssetSupport.toJson(entries));
        int rows = testProjectMapper.updateAssetVariables(row);
        if (rows <= 0) {
            throw new ServiceException("保存素材库失败");
        }
    }

    /**
     * 确保项目存在
     */
    private void assertProjectExists(Long testProjectId) {
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        if (project == null || (project.getDelStatus() != null && project.getDelStatus() != 0)) {
            throw new ServiceException("测试项目不存在或已删除");
        }
    }

}
