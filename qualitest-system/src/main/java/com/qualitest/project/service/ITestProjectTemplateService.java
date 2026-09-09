package com.qualitest.project.service;

import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;

import java.util.List;

/**
 * 项目模板Service接口
 *
 * @author qualitest
 * @date 2026-09-09
 */
public interface ITestProjectTemplateService {

    /**
     * 查询项目模板列表
     *
     * @param testProjectTemplate 项目模板
     * @return 项目模板集合
     */
    List<TestProjectTemplate> selectTestProjectTemplateList(TestProjectTemplate testProjectTemplate);

    /**
     * 查询项目模板
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 项目模板
     */
    TestProjectTemplate selectTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 查询项目模板Result列表
     *
     * @param params 项目模板Params
     * @return 项目模板Result集合
     */
    List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params);

    /**
     * 获取项目模板详细信息
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 项目模板Result
     */
    TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId);

    /**
     * 新增项目模板（管理端 CRUD：预制测试流落库为空）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    int insertTestProjectTemplate(TestProjectTemplate testProjectTemplate);

    /**
     * 修改项目模板（管理端 CRUD：不改写库内预制测试流）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    int updateTestProjectTemplate(TestProjectTemplate testProjectTemplate);

    /**
     * 新增项目模板（完整包导入 / 另存：允许写入预制测试流）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    int insertTemplatePack(TestProjectTemplate testProjectTemplate);

    /**
     * 修改项目模板（完整包同名覆盖：允许改写预制测试流）
     *
     * @param testProjectTemplate 项目模板
     * @return 结果
     */
    int updateTemplatePack(TestProjectTemplate testProjectTemplate);

    /**
     * 批量删除项目模板
     *
     * @param testProjectTemplateIdList 需要删除的项目模板主键集合
     * @return 结果
     */
    int deleteTestProjectTemplateByIdList(List<Long> testProjectTemplateIdList);

    /**
     * 删除项目模板信息
     *
     * @param testProjectTemplateId 项目模板主键
     * @return 结果
     */
    int deleteTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 修改项目模板为逻辑删除
     *
     * @param testProjectTemplateId 项目模板ID
     * @return 结果
     */
    int logicDeleteTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 批量修改项目模板为逻辑删除
     *
     * @param testProjectTemplateIdList 项目模板ID集合
     * @return 结果
     */
    int logicDeleteTestProjectTemplateByIdList(List<Long> testProjectTemplateIdList);

    /**
     * 查询项目模板数量
     *
     * @param params 项目模板Params
     * @return 数量
     */
    int selectTestProjectTemplateCount(TestProjectTemplateParams params);

    /**
     * 按条件查询单条项目模板
     *
     * @param params 项目模板Params
     * @return 项目模板
     */
    TestProjectTemplate selectTestProjectTemplateOne(TestProjectTemplateParams params);

    /**
     * 查询已启用且未删除的项目模板列表（新建项目 / 项目设置勾选）
     *
     * @return 项目模板Result集合
     */
    List<TestProjectTemplateResult> selectEnabledList();

    /**
     * 克隆为自定义项目模板（带上源模板预制流）
     *
     * @param testProjectTemplateId 源模板主键
     * @return 新模板主键
     */
    Long cloneTestProjectTemplate(Long testProjectTemplateId);
}
