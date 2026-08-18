package com.qualitest.project.service;

import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;

import java.util.List;

/**
 * 鉴权模板：增删改查、克隆、列出启用项。
 */
public interface ITestProjectTemplateService {

    /** 分页列表。 */
    List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params);

    /** 按 id 查一条未删除模板。 */
    TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId);

    /** 已启用且未删除的模板，供新建/设置勾选。 */
    List<TestProjectTemplateResult> selectEnabledList();

    /** 按 id 查实体，含已删判断由调用方处理。 */
    TestProjectTemplate selectTestProjectTemplateById(Long testProjectTemplateId);

    /** 新增自定义模板。 */
    int insertTestProjectTemplate(TestProjectTemplate entity);

    /** 修改自定义模板；内置不可改。 */
    int updateTestProjectTemplate(TestProjectTemplate entity);

    /** 逻辑删除自定义模板；内置不可删。 */
    int logicDeleteTestProjectTemplateByIdList(List<Long> idList);

    /**
     * 克隆一份为自定义模板。
     *
     * @return 新模板 id
     */
    Long cloneTestProjectTemplate(Long testProjectTemplateId);
}
