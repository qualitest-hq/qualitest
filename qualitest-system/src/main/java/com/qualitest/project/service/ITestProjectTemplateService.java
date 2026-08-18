package com.qualitest.project.service;

import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;

import java.util.List;

/**
 * 鉴权模板：增删改查、克隆、列出启用项。
 */
public interface ITestProjectTemplateService {

    /**
     * 查询模板列表。
     *
     * @param entity 查询条件
     * @return 模板实体集合
     */
    List<TestProjectTemplate> selectTestProjectTemplateList(TestProjectTemplate entity);

    /**
     * 按主键查询模板实体。
     *
     * @param testProjectTemplateId 模板ID
     * @return 模板实体
     */
    TestProjectTemplate selectTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 查询模板 Result 列表。
     *
     * @param params 查询条件
     * @return 模板结果集合
     */
    List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params);

    /**
     * 查询单条未删除模板结果。
     *
     * @param testProjectTemplateId 模板ID
     * @return 模板结果；不存在时返回 null
     */
    TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId);

    /**
     * 新增自定义模板。
     *
     * @param entity 模板实体
     * @return 新增行数
     */
    int insertTestProjectTemplate(TestProjectTemplate entity);

    /**
     * 修改自定义模板。
     *
     * @param entity 模板实体
     * @return 更新行数；内置模板不可修改
     */
    int updateTestProjectTemplate(TestProjectTemplate entity);

    /**
     * 批量物理删除自定义模板。
     *
     * @param idList 模板ID列表
     * @return 删除行数；内置模板不可删除
     */
    int deleteTestProjectTemplateByIdList(List<Long> idList);

    /**
     * 单条物理删除自定义模板。
     *
     * @param testProjectTemplateId 模板ID
     * @return 删除行数；内置模板不可删除
     */
    int deleteTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 单条逻辑删除自定义模板。
     *
     * @param testProjectTemplateId 模板ID
     * @return 删除行数；内置模板不可删除
     */
    int logicDeleteTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 批量逻辑删除自定义模板。
     *
     * @param idList 模板ID列表
     * @return 删除行数；内置模板不可删除
     */
    int logicDeleteTestProjectTemplateByIdList(List<Long> idList);

    /**
     * 查询模板数量。
     *
     * @param params 查询条件
     * @return 模板数量
     */
    int selectTestProjectTemplateCount(TestProjectTemplateParams params);

    /**
     * 按条件查询单条模板实体。
     *
     * @param params 查询条件
     * @return 模板实体；查不到时返回 null
     */
    TestProjectTemplate selectTestProjectTemplateOne(TestProjectTemplateParams params);

    /**
     * 查询已启用且未删除的模板列表。
     *
     * @return 可供新建/设置勾选的模板列表
     */
    List<TestProjectTemplateResult> selectEnabledList();

    /**
     * 克隆一份为自定义模板。
     *
     * @param testProjectTemplateId 源模板ID
     * @return 新模板 id
     */
    Long cloneTestProjectTemplate(Long testProjectTemplateId);
}
