package com.qualitest.ai.service;

import java.util.List;
import com.qualitest.ai.domain.AiLlmModel;
import com.qualitest.ai.params.AiLlmModelParams;
import com.qualitest.ai.result.AiLlmModelResult;
import com.qualitest.ai.result.AiModelsListResult;
import com.qualitest.ai.llm.LlmModelConfig;

/**
 * AI 模型业务接口。
 * <p>
 * 除 CRUD 外，提供 {@link #resolve(Long)} 供 LLM 运行时拉取厂商密钥与超时，
 * 以及 {@link #listModelsGrouped()} 供设计面板按厂商分组展示模型（不含 apiKey）。
 */
public interface IAiLlmModelService {
    /**
     * 查询AI 模型列表
     *
     * @param aiLlmModel AI 模型
     * @return AI 模型集合
     */
    List<AiLlmModel> selectAiLlmModelList(AiLlmModel aiLlmModel);

    /**
     * 查询AI 模型
     *
     * @param aiLlmModelId AI 模型主键
     * @return AI 模型
     */
    AiLlmModel selectAiLlmModelById(Long aiLlmModelId);

    /**
     * 查询AI 模型Result列表
     *
     * @param params AI 模型Params
     * @return AI 模型Result集合
     */
    List<AiLlmModelResult> selectAiLlmModelResultList(AiLlmModelParams params);

    /**
     * 获取AI 模型详细信息
     *
     * @param aiLlmModelId AI 模型主键
     * @return AI 模型Result
     */
    AiLlmModelResult selectAiLlmModelResult(Long aiLlmModelId);

    /**
     * 新增AI 模型
     * 
     * @param aiLlmModel AI 模型
     * @return 结果
     */
    int insertAiLlmModel(AiLlmModel aiLlmModel);

    /**
     * 修改AI 模型
     * 
     * @param aiLlmModel AI 模型
     * @return 结果
     */
    int updateAiLlmModel(AiLlmModel aiLlmModel);

    /**
     * 批量删除AI 模型
     * 
     * @param aiLlmModelIdList 需要删除的AI 模型主键集合
     * @return 结果
     */
    int deleteAiLlmModelByIdList(List<Long> aiLlmModelIdList);

    /**
     * 删除AI 模型信息
     * 
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    public int deleteAiLlmModelById(Long aiLlmModelId);

    /**
     * 修改AI 模型为逻辑删除
     *
     * @param aiLlmModelId AI 模型ID
     * @return 结果
     */
    int logicDeleteAiLlmModelById(Long aiLlmModelId);

    /**
     * 批量修改AI 模型为逻辑删除
     *
     * @param aiLlmModelIdList AI 模型ID集合
     * @return 结果
     */
    int logicDeleteAiLlmModelByIdList(List<Long> aiLlmModelIdList);

    /**
     * 查询AI 模型数量
     *
     * @param params AI 模型Params
     * @return 数量
     */
    int selectAiLlmModelCount(AiLlmModelParams params);

    /**
     * 按条件查询单条AI 模型
     *
     * @param params AI 模型Params
     * @return AI 模型
     */
    AiLlmModel selectAiLlmModelOne(AiLlmModelParams params);

    /**
     * 解析模型调用配置：JOIN 厂商表取 base_url、api_key，并合并全局超时与 max_tokens。
     * 仅当厂商与模型均为启用且未删除时可用。
     *
     * @param aiLlmModelId 模型主键
     * @return 供 {@link com.qualitest.ai.llm.LlmProvider} 使用的运行时配置
     */
    LlmModelConfig resolve(Long aiLlmModelId);

    /**
     * 查询所有启用模型，按厂商 sort_num 分组，供设计面板下拉使用。
     * 响应不含 apiKey。
     *
     * @return 厂商分组列表；defaultModelId 为排序后首项
     */
    AiModelsListResult listModelsGrouped();
}
