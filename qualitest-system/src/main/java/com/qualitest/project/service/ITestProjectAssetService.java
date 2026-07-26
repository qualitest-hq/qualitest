package com.qualitest.project.service;

import com.qualitest.project.params.TestProjectAssetParams;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;

import java.util.List;

/**
 * 项目素材库 Service
 *
 * @author qualitest
 */
public interface ITestProjectAssetService {

    /**
     * 查询项目下素材列表
     */
    List<TestProjectAssetResult> selectTestProjectAssetResultList(TestProjectAssetParams params);

    /**
     * 按条目 id 查询单条
     */
    TestProjectAssetResult selectTestProjectAssetResult(Long testProjectId, Long id);

    /**
     * 按 key 查询单条
     */
    TestProjectAssetResult selectTestProjectAssetResultByKey(Long testProjectId, String key);

    /**
     * 新增素材条目
     */
    TestProjectAssetResult insertTestProjectAsset(TestProjectAssetSaveParams params);

    /**
     * 修改素材条目
     */
    TestProjectAssetResult updateTestProjectAsset(TestProjectAssetSaveParams params);

    /**
     * 批量删除素材条目
     */
    int deleteTestProjectAssetByIds(Long testProjectId, List<Long> idList);

}
