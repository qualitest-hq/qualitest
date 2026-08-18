package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestProjectTemplate;
import com.qualitest.project.params.TestProjectTemplateParams;
import com.qualitest.project.result.TestProjectTemplateResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 鉴权模板表访问。
 */
@Mapper
public interface TestProjectTemplateMapper {

    /** 按主键查实体。 */
    TestProjectTemplate selectTestProjectTemplateById(Long testProjectTemplateId);

    /** 未删除列表，可按名称/内置/启用过滤。 */
    List<TestProjectTemplateResult> selectTestProjectTemplateResultList(TestProjectTemplateParams params);

    /** 按主键查未删除结果。 */
    TestProjectTemplateResult selectTestProjectTemplateResult(Long testProjectTemplateId);

    /** 已启用且未删除，按 sort_num 排序。 */
    List<TestProjectTemplateResult> selectEnabledTestProjectTemplateList();

    /**
     * 未删除范围内同名条数。
     *
     * @param excludeId 修改时排除自身，新增传 null
     */
    int countByTemplateName(@Param("templateName") String templateName,
                            @Param("excludeId") Long excludeId);

    int insertTestProjectTemplate(TestProjectTemplate entity);

    int updateTestProjectTemplate(TestProjectTemplate entity);

    int logicDeleteTestProjectTemplateById(Long testProjectTemplateId);

    int logicDeleteTestProjectTemplateByIdList(@Param("list") List<Long> idList);
}
