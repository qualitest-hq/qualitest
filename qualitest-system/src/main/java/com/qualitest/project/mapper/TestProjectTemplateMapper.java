package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目模板表访问。
 */
@Mapper
public interface TestProjectTemplateMapper {

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
     * 查询未删除模板 Result 列表。
     *
     * @param params 名称、内置状态、启用状态等筛选条件
     * @return 模板结果列表
     */
    List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params);

    /**
     * 按主键查询未删除模板结果。
     *
     * @param testProjectTemplateId 模板ID
     * @return 模板结果
     */
    TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId);

    /**
     * 新增模板。
     *
     * @param entity 模板实体
     * @return 新增行数
     */
    int insertTestProjectTemplate(TestProjectTemplate entity);

    /**
     * 修改模板。
     *
     * @param entity 模板实体
     * @return 更新行数
     */
    int updateTestProjectTemplate(TestProjectTemplate entity);

    /**
     * 批量物理删除模板。
     *
     * @param idList 模板ID列表
     * @return 删除行数
     */
    int deleteTestProjectTemplateByIdList(@Param("list") List<Long> idList);

    /**
     * 单条物理删除模板。
     *
     * @param testProjectTemplateId 模板ID
     * @return 删除行数
     */
    int deleteTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 单条逻辑删除模板。
     *
     * @param testProjectTemplateId 模板ID
     * @return 删除行数
     */
    int logicDeleteTestProjectTemplateById(Long testProjectTemplateId);

    /**
     * 批量逻辑删除模板。
     *
     * @param idList 模板ID列表
     * @return 删除行数
     */
    int logicDeleteTestProjectTemplateByIdList(@Param("list") List<Long> idList);

    /**
     * 查询模板数量。
     *
     * @param params 名称、内置状态、启用状态等筛选条件
     * @return 模板数量
     */
    int selectTestProjectTemplateCount(TestProjectTemplateParams params);

    /**
     * 按条件查询单条模板实体。
     *
     * @param params 名称、内置状态、启用状态等筛选条件
     * @return 模板实体
     */
    TestProjectTemplate selectTestProjectTemplateOne(TestProjectTemplateParams params);

    /**
     * 查询已启用且未删除的模板列表。
     *
     * @return 按 sort_num 排序的模板结果列表
     */
    List<TestProjectTemplateResult> selectEnabledTestProjectTemplateList();

    /**
     * 未删除范围内同名条数。
     *
     * @param templateName 模板名称
     * @param excludeId 修改时排除自身，新增传 null
     * @return 同名模板数量
     */
    int countByTemplateName(@Param("templateName") String templateName,
                            @Param("excludeId") Long excludeId);
}
