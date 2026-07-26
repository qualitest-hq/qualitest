package com.qualitest.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.ai.domain.AiLlmVendor;
import com.qualitest.ai.params.AiLlmVendorParams;
import com.qualitest.ai.result.AiLlmVendorResult;

/**
 * AI 厂商Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Mapper
public interface AiLlmVendorMapper {
    /**
     * 查询AI 厂商列表
     *
     * @param aiLlmVendor AI 厂商
     * @return AI 厂商集合
     */
    List<AiLlmVendor> selectAiLlmVendorList(AiLlmVendor aiLlmVendor);

    /**
     * 查询AI 厂商
     *
     * @param aiLlmVendorId AI 厂商主键
     * @return AI 厂商
     */
    AiLlmVendor selectAiLlmVendorById(Long aiLlmVendorId);

    /**
     * 查询AI 厂商Result列表
     *
     * @param params AI 厂商Params
     * @return AI 厂商Result集合
     */
    List<AiLlmVendorResult> selectAiLlmVendorResultList(AiLlmVendorParams params);

    /**
     * 获取AI 厂商详细信息
     *
     * @param aiLlmVendorId AI 厂商主键
     * @return AI 厂商Result
     */
    AiLlmVendorResult selectAiLlmVendorResult(Long aiLlmVendorId);

    /**
     * 查询AI 厂商数量
     *
     * @param params AI 厂商Params
     * @return 数量
     */
    int selectAiLlmVendorCount(AiLlmVendorParams params);

    /**
     * 按条件查询单条AI 厂商
     *
     * @param params AI 厂商Params
     * @return AI 厂商
     */
    AiLlmVendor selectAiLlmVendorOne(AiLlmVendorParams params);

    /**
     * 新增AI 厂商
     * 
     * @param aiLlmVendor AI 厂商
     * @return 结果
     */
    int insertAiLlmVendor(AiLlmVendor aiLlmVendor);

    /**
     * 修改AI 厂商
     * 
     * @param aiLlmVendor AI 厂商
     * @return 结果
     */
    int updateAiLlmVendor(AiLlmVendor aiLlmVendor);

    /**
     * 删除AI 厂商
     * 
     * @param aiLlmVendorId AI 厂商主键
     * @return 结果
     */
    int deleteAiLlmVendorById(Long aiLlmVendorId);

    /**
     * 批量删除AI 厂商
     * 
     * @param aiLlmVendorIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAiLlmVendorByIdList(@Param("list") List<Long> aiLlmVendorIdList);

    /**
     * 逻辑删除AI 厂商
     * 
     * @param aiLlmVendorId AI 厂商主键
     * @return 结果
     */
    int logicDeleteAiLlmVendorById(Long aiLlmVendorId);

    /**
     * 批量逻辑删除AI 厂商
     * 
     * @param aiLlmVendorIdList AI 厂商主键集合
     * @return 结果
     */
    int logicDeleteAiLlmVendorByIdList(@Param("list") List<Long> aiLlmVendorIdList);
}
