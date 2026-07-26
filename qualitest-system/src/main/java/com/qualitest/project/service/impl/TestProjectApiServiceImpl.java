package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.diagnose.ApiFlowHealthPersistService;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.params.TestProjectApiParams;
import com.qualitest.project.result.TestProjectApiResult;
import com.qualitest.project.service.ITestProjectApiService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Date;

/**
 * 测试项目APIService业务层处理
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Service
public class TestProjectApiServiceImpl implements ITestProjectApiService {

    /** 单次批量 INSERT 的最大条数，防止 SQL 过长 */
    private static final int INSERT_BATCH_SIZE = 100;

    @Autowired
    private TestProjectApiMapper testProjectApiMapper;

    @Autowired
    private ITestProjectService testProjectService;

    /** 删除/变更 API 后，回写引用流的 api_health_* 字段 */
    @Autowired
    private ApiFlowHealthPersistService apiFlowHealthPersistService;

    /**
     * 查询测试项目API列表
     *
     * @param testProjectApi 测试项目API
     * @return 测试项目API
     */
    @Override
    public List<TestProjectApi> selectTestProjectApiList(TestProjectApi testProjectApi) {
        return testProjectApiMapper.selectTestProjectApiList(testProjectApi);
    }

    /**
     * 查询测试项目API
     *
     * @param testProjectApiId 测试项目API主键
     * @return 测试项目API
     */
    @Override
    public TestProjectApi selectTestProjectApiById(Long testProjectApiId) {
        return testProjectApiMapper.selectTestProjectApiById(testProjectApiId);
    }

    /**
     * 查询测试项目APIResult列表
     *
     * @param params 测试项目APIParams
     * @return 测试项目APIResult集合
     */
    @Override
    public List<TestProjectApiResult> selectTestProjectApiResultList(TestProjectApiParams params) {
        return testProjectApiMapper.selectTestProjectApiResultList(params);
    }

    /**
     * 获取 API 详情；返回前将 test_value_config 叠加到 request/response，供调试/设计页直接展示有效配置。
     */
    @Override
    public TestProjectApiResult selectTestProjectApiResult(Long testProjectApiId) {
        TestProjectApiResult result = testProjectApiMapper.selectTestProjectApiResult(testProjectApiId);
        return applyEffectiveConfigForRead(result);
    }

    /**
     * 单条新增测试项目 API，写入后刷新所属项目的 api_count。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectApi(TestProjectApi testProjectApi) {
        if (Objects.isNull(testProjectApi.getTestProjectApiId())) {
            testProjectApi.setTestProjectApiId(IdUtil.getSnowflakeNextId());
        }
        testProjectApi.setCreateTime(DateUtils.getNowDate());
        int rows = testProjectApiMapper.insertTestProjectApi(testProjectApi);
        refreshProjectApiCount(testProjectApi.getTestProjectId());
        return rows;
    }

    /**
     * 批量新增测试项目 API，按 INSERT_BATCH_SIZE 拆成多段执行。
     * 不刷新 api_count，由导入流程在全部落库后统一调用 refreshApiCount。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int batchInsertTestProjectApi(List<TestProjectApi> testProjectApiList) {
        if (testProjectApiList == null || testProjectApiList.isEmpty()) {
            return 0;
        }
        Date now = DateUtils.getNowDate();
        for (TestProjectApi api : testProjectApiList) {
            if (Objects.isNull(api.getTestProjectApiId())) {
                api.setTestProjectApiId(IdUtil.getSnowflakeNextId());
            }
            if (api.getCreateTime() == null) {
                api.setCreateTime(now);
            }
        }
        int total = 0;
        for (int i = 0; i < testProjectApiList.size(); i += INSERT_BATCH_SIZE) {
            int end = Math.min(i + INSERT_BATCH_SIZE, testProjectApiList.size());
            total += testProjectApiMapper.batchInsertTestProjectApi(testProjectApiList.subList(i, end));
        }
        return total;
    }

    /**
     * 修改测试项目API
     *
     * @param testProjectApi 测试项目API
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectApi(TestProjectApi testProjectApi) {
        testProjectApi.setUpdateTime(DateUtils.getNowDate());
        return testProjectApiMapper.updateTestProjectApi(testProjectApi);
    }

    /**
     * 批量删除测试项目API
     *
     * @param testProjectApiIdList 需要删除的测试项目API主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectApiByIdList(List<Long> testProjectApiIdList) {
        return testProjectApiMapper.deleteTestProjectApiByIdList(testProjectApiIdList);
    }

    /**
     * 删除测试项目API信息
     *
     * @param testProjectApiId 测试项目API主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectApiById(Long testProjectApiId) {
        return testProjectApiMapper.deleteTestProjectApiById(testProjectApiId);
    }

    /**
     * 逻辑删除测试项目API信息
     *
     * @param testProjectApiId 测试项目API主键
     * @return 结果
     */
    /**
     * 逻辑删除单个测试项目 API。
     * 内部走批量删除逻辑：删前按项目归组，删后刷新项目 api_count，并对引用流回写 api_health_*。
     */
    @Override
    public int logicDeleteTestProjectApiById(Long testProjectApiId) {
        return logicDeleteTestProjectApiByIdList(
                testProjectApiId == null ? List.of() : List.of(testProjectApiId));
    }

    /**
     * 批量逻辑删除测试项目 API。
     * <p>
     * 删除前按所属项目归组 API id；删除成功后：
     * 刷新各项目的 api_count；对仍绑定这些 API id 的测试流重新体检并回写 api_health_*
     * （常见结果为 API_MISSING）。
     *
     * @param testProjectApiIdList 待删除的 API id 列表
     * @return 影响行数
     */
    @Override
    public int logicDeleteTestProjectApiByIdList(List<Long> testProjectApiIdList) {
        Map<Long, List<Long>> apisByProject = groupApiIdsByProject(testProjectApiIdList);
        List<Long> projectIds = testProjectApiMapper.selectDistinctTestProjectIdsByApiIdList(testProjectApiIdList);
        int rows = testProjectApiMapper.logicDeleteTestProjectApiByIdList(testProjectApiIdList);
        if (rows > 0) {
            projectIds.forEach(this::refreshProjectApiCount);
            for (Map.Entry<Long, List<Long>> entry : apisByProject.entrySet()) {
                apiFlowHealthPersistService.refreshByApiIds(entry.getKey(), entry.getValue());
            }
        }
        return rows;
    }

    /**
     * 删除前查询每条 API 所属项目，得到「项目 id → API id 列表」映射，
     * 供删除后按项目回写引用流的健康字段。
     */
    private Map<Long, List<Long>> groupApiIdsByProject(List<Long> testProjectApiIdList) {
        Map<Long, List<Long>> apisByProject = new HashMap<>();
        if (testProjectApiIdList == null || testProjectApiIdList.isEmpty()) {
            return apisByProject;
        }
        for (Long apiId : testProjectApiIdList) {
            if (apiId == null) {
                continue;
            }
            TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
            if (api != null && api.getTestProjectId() != null) {
                apisByProject.computeIfAbsent(api.getTestProjectId(), k -> new ArrayList<>()).add(apiId);
            }
        }
        return apisByProject;
    }

    /**
     * 查询测试项目API数量
     *
     * @param params 测试项目APIParams
     * @return 数量
     */
    @Override
    public int selectTestProjectApiCount(TestProjectApiParams params) {
        return testProjectApiMapper.selectTestProjectApiCount(params);
    }

    /**
     * 按条件查询单条测试项目API
     *
     * @param params 测试项目APIParams
     * @return 测试项目API
     */
    @Override
    public TestProjectApi selectTestProjectApiOne(TestProjectApiParams params) {
        return testProjectApiMapper.selectTestProjectApiOne(params);
    }

    /**
     * 按 test_project_api 实际未删除条数，回写 test_project.api_count。
     */
    private void refreshProjectApiCount(Long testProjectId) {
        if (testProjectId != null) {
            testProjectService.refreshApiCount(testProjectId);
        }
    }

    /**
     * 读详情后置：把测试值层中的默认值、示例叠到 Result 的 requestConfig / responseConfig，再返回前端。
     */
    private TestProjectApiResult applyEffectiveConfigForRead(TestProjectApiResult result) {
        TestProjectApiEffectiveConfigResolver.overlayResultConfigs(result);
        return result;
    }
}
