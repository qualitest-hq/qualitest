package com.qualitest.ai.service;

import java.util.List;
import com.qualitest.ai.domain.AiPromptTemplate;
import com.qualitest.ai.params.AiPromptTemplateParams;
import com.qualitest.ai.result.AiPromptTemplateResult;

/**
 * AI提示词模板Service接口
 * 
 * @author qualitest
 * @date 2026-07-04
 */
public interface IAiPromptTemplateService {
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
     * 批量删除AI提示词模板
     * 
     * @param aiPromptTemplateIdList 需要删除的AI提示词模板主键集合
     * @return 结果
     */
    int deleteAiPromptTemplateByIdList(List<Long> aiPromptTemplateIdList);

    /**
     * 删除AI提示词模板信息
     * 
     * @param aiPromptTemplateId AI提示词模板主键
     * @return 结果
     */
    public int deleteAiPromptTemplateById(Long aiPromptTemplateId);

    /**
     * 修改AI提示词模板为逻辑删除
     *
     * @param aiPromptTemplateId AI提示词模板ID
     * @return 结果
     */
    int logicDeleteAiPromptTemplateById(Long aiPromptTemplateId);

    /**
     * 批量修改AI提示词模板为逻辑删除
     *
     * @param aiPromptTemplateIdList AI提示词模板ID集合
     * @return 结果
     */
    int logicDeleteAiPromptTemplateByIdList(List<Long> aiPromptTemplateIdList);

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
     * AI 设计面板：启用的平台级 + 指定项目级模板列表。
     */
    List<AiPromptTemplateResult> listForDesignPanel(Long testProjectId, String sessionScene);
}
