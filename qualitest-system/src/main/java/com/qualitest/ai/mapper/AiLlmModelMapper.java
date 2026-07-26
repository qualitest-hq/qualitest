package com.qualitest.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.ai.domain.AiLlmModel;
import com.qualitest.ai.params.AiLlmModelParams;
import com.qualitest.ai.result.AiLlmModelResult;
import com.qualitest.ai.result.AiLlmModelResolveResult;

/**
 * AI 模型Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Mapper
public interface AiLlmModelMapper {
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
     * 删除AI 模型
     * 
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    int deleteAiLlmModelById(Long aiLlmModelId);

    /**
     * 批量删除AI 模型
     * 
     * @param aiLlmModelIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAiLlmModelByIdList(@Param("list") List<Long> aiLlmModelIdList);

    /**
     * 逻辑删除AI 模型
     * 
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    int logicDeleteAiLlmModelById(Long aiLlmModelId);

    /**
     * 批量逻辑删除AI 模型
     * 
     * @param aiLlmModelIdList AI 模型主键集合
     * @return 结果
     */
    int logicDeleteAiLlmModelByIdList(@Param("list") List<Long> aiLlmModelIdList);

    /**
     * JOIN 厂商解析模型调用配置（含 apiKey，仅服务端使用）。
     */
    AiLlmModelResolveResult selectAiLlmModelResolve(Long aiLlmModelId);

    /**
     * 查询所有启用且未删除的模型及其厂商信息，按 sort_num 排序。
     */
    List<AiLlmModelResolveResult> selectEnabledAiLlmModelsWithVendor();
}
