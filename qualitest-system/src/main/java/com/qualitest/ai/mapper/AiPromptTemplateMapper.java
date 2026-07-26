package com.qualitest.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.ai.domain.AiPromptTemplate;
import com.qualitest.ai.params.AiPromptTemplateParams;
import com.qualitest.ai.result.AiPromptTemplateResult;

/**
 * AI提示词模板Mapper接口
 * 
 * @author qualitest
 * @date 2026-07-04
 */
@Mapper
public interface AiPromptTemplateMapper {
    /**
     * 查询AI提示词模板列表
     *
     * @param aiPromptTemplate AI提示词模板
     * @return AI提示词模板集合
     */
    List<AiPromptTemplate> selectAiPromptTemplateList(AiPromptTemplate aiPromptTemplate);

    /**
     * 查询AI提示词模板
     *
     * @param aiPromptTemplateId AI提示词模板主键
     * @return AI提示词模板
     */
    AiPromptTemplate selectAiPromptTemplateById(Long aiPromptTemplateId);

    /**
     * 查询AI提示词模板Result列表
     *
     * @param params AI提示词模板Params
     * @return AI提示词模板Result集合
     */
    List<AiPromptTemplateResult> selectAiPromptTemplateResultList(AiPromptTemplateParams params);

    /**
     * 获取AI提示词模板详细信息
     *
     * @param aiPromptTemplateId AI提示词模板主键
     * @return AI提示词模板Result
     */
    AiPromptTemplateResult selectAiPromptTemplateResult(Long aiPromptTemplateId);

    /**
     * 查询AI提示词模板数量
     *
     * @param params AI提示词模板Params
     * @return 数量
     */
    int selectAiPromptTemplateCount(AiPromptTemplateParams params);

    /**
     * 按条件查询单条AI提示词模板
     *
     * @param params AI提示词模板Params
     * @return AI提示词模板
     */
    AiPromptTemplate selectAiPromptTemplateOne(AiPromptTemplateParams params);

    /**
     * AI 设计面板：查询启用的平台级 + 指定项目级模板。
     */
    List<AiPromptTemplateResult> selectAiPromptTemplateForDesignPanel(
            @Param("testProjectId") Long testProjectId,
            @Param("sessionScene") String sessionScene);

    /**
     * 新增AI提示词模板
     * 
     * @param aiPromptTemplate AI提示词模板
     * @return 结果
     */
    int insertAiPromptTemplate(AiPromptTemplate aiPromptTemplate);

    /**
     * 修改AI提示词模板
     * 
     * @param aiPromptTemplate AI提示词模板
     * @return 结果
     */
    int updateAiPromptTemplate(AiPromptTemplate aiPromptTemplate);

    /**
     * 删除AI提示词模板
     * 
     * @param aiPromptTemplateId AI提示词模板主键
     * @return 结果
     */
    int deleteAiPromptTemplateById(Long aiPromptTemplateId);

    /**
     * 批量删除AI提示词模板
     * 
     * @param aiPromptTemplateIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAiPromptTemplateByIdList(@Param("list") List<Long> aiPromptTemplateIdList);

    /**
     * 逻辑删除AI提示词模板
     * 
     * @param aiPromptTemplateId AI提示词模板主键
     * @return 结果
     */
    int logicDeleteAiPromptTemplateById(Long aiPromptTemplateId);

    /**
     * 批量逻辑删除AI提示词模板
     * 
     * @param aiPromptTemplateIdList AI提示词模板主键集合
     * @return 结果
     */
    int logicDeleteAiPromptTemplateByIdList(@Param("list") List<Long> aiPromptTemplateIdList);
}
