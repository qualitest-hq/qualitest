package com.qualitest.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.ai.llm.discovery.ModelDiscoveryService;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.ai.mapper.AiLlmVendorMapper;
import com.qualitest.ai.domain.AiLlmVendor;
import com.qualitest.ai.params.AiLlmVendorParams;
import com.qualitest.ai.result.AiLlmVendorResult;
import com.qualitest.ai.service.IAiLlmVendorService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

/**
 * AI 厂商 Service：CRUD、密钥脱敏更新、内置删除保护及发现缓存失效。
 *
 * @author qualitest
 * @date 2026-06-15
 */
@Service
public class AiLlmVendorServiceImpl implements IAiLlmVendorService {
    @Autowired
    private AiLlmVendorMapper aiLlmVendorMapper;

    /** 延迟注入，避免与 ModelDiscoveryService 循环依赖 */
    @Lazy
    @Autowired
    private ModelDiscoveryService modelDiscoveryService;

    /** 管理端脱敏占位符；编辑时传此值表示不修改原密钥 */
    private static final String MASKED_API_KEY = "******";

    /**
     * 查询AI 厂商列表
     *
     * @param aiLlmVendor AI 厂商
     * @return AI 厂商
     */
    @Override
    public List<AiLlmVendor> selectAiLlmVendorList(AiLlmVendor aiLlmVendor) {
        return aiLlmVendorMapper.selectAiLlmVendorList(aiLlmVendor);
    }

    /**
     * 查询AI 厂商
     *
     * @param aiLlmVendorId AI 厂商主键
     * @return AI 厂商
     */
    @Override
    public AiLlmVendor selectAiLlmVendorById(Long aiLlmVendorId) {
        return aiLlmVendorMapper.selectAiLlmVendorById(aiLlmVendorId);
    }

    /**
     * 查询AI 厂商Result列表
     *
     * @param params AI 厂商Params
     * @return AI 厂商Result集合
     */
    @Override
    public List<AiLlmVendorResult> selectAiLlmVendorResultList(AiLlmVendorParams params) {
        return aiLlmVendorMapper.selectAiLlmVendorResultList(params);
    }

    /**
     * 获取AI 厂商详细信息
     *
     * @param aiLlmVendorId AI 厂商主键
     * @return AI 厂商Result
     */
    @Override
    public AiLlmVendorResult selectAiLlmVendorResult(Long aiLlmVendorId) {
        return aiLlmVendorMapper.selectAiLlmVendorResult(aiLlmVendorId);
    }

    /**
     * 新增AI 厂商
     *
     * @param aiLlmVendor AI 厂商
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertAiLlmVendor(AiLlmVendor aiLlmVendor) {
        if (Objects.isNull(aiLlmVendor.getAiLlmVendorId())) {
            aiLlmVendor.setAiLlmVendorId(IdUtil.getSnowflakeNextId());
        }
        aiLlmVendor.setCreateTime(DateUtils.getNowDate());
        return aiLlmVendorMapper.insertAiLlmVendor(aiLlmVendor);
    }

    /**
     * 修改厂商；脱敏 apiKey 不覆盖原值。baseUrl 或 apiKey 变更时失效发现缓存。
     *
     * @param aiLlmVendor AI 厂商
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateAiLlmVendor(AiLlmVendor aiLlmVendor) {
        if (aiLlmVendor.getAiLlmVendorId() != null) {
            AiLlmVendor existing = aiLlmVendorMapper.selectAiLlmVendorById(aiLlmVendor.getAiLlmVendorId());
            if (existing != null) {
                if (isMaskedApiKey(aiLlmVendor.getApiKey())) {
                    aiLlmVendor.setApiKey(existing.getApiKey());
                }
                boolean connectionChanged = !Objects.equals(existing.getApiKey(), aiLlmVendor.getApiKey())
                        || !Objects.equals(existing.getBaseUrl(), aiLlmVendor.getBaseUrl());
                aiLlmVendor.setUpdateTime(DateUtils.getNowDate());
                int rows = aiLlmVendorMapper.updateAiLlmVendor(aiLlmVendor);
                if (connectionChanged) {
                    modelDiscoveryService.invalidateDiscoverCache(aiLlmVendor.getAiLlmVendorId());
                }
                return rows;
            }
        }
        aiLlmVendor.setUpdateTime(DateUtils.getNowDate());
        return aiLlmVendorMapper.updateAiLlmVendor(aiLlmVendor);
    }

    /**
     * 批量物理删除厂商；内置厂商拒绝，删除前失效发现缓存。
     *
     * @param aiLlmVendorIdList 需要删除的 AI 厂商主键集合
     * @return 结果
     */
    @Override
    public int deleteAiLlmVendorByIdList(List<Long> aiLlmVendorIdList) {
        if (aiLlmVendorIdList == null || aiLlmVendorIdList.isEmpty()) {
            return 0;
        }
        for (Long id : aiLlmVendorIdList) {
            assertDeletable(id);
            modelDiscoveryService.invalidateDiscoverCache(id);
        }
        return aiLlmVendorMapper.deleteAiLlmVendorByIdList(aiLlmVendorIdList);
    }

    /**
     * 物理删除厂商；内置厂商拒绝，删除前失效发现缓存。
     *
     * @param aiLlmVendorId AI 厂商主键
     * @return 结果
     */
    @Override
    public int deleteAiLlmVendorById(Long aiLlmVendorId) {
        assertDeletable(aiLlmVendorId);
        modelDiscoveryService.invalidateDiscoverCache(aiLlmVendorId);
        return aiLlmVendorMapper.deleteAiLlmVendorById(aiLlmVendorId);
    }

    /**
     * 逻辑删除厂商；内置厂商拒绝，删除前失效发现缓存。
     *
     * @param aiLlmVendorId AI 厂商主键
     * @return 结果
     */
    @Override
    public int logicDeleteAiLlmVendorById(Long aiLlmVendorId) {
        assertDeletable(aiLlmVendorId);
        modelDiscoveryService.invalidateDiscoverCache(aiLlmVendorId);
        return aiLlmVendorMapper.logicDeleteAiLlmVendorById(aiLlmVendorId);
    }

    /**
     * 批量逻辑删除厂商；内置厂商拒绝，删除前失效发现缓存。
     *
     * @param aiLlmVendorIdList AI 厂商主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteAiLlmVendorByIdList(List<Long> aiLlmVendorIdList) {
        if (aiLlmVendorIdList == null || aiLlmVendorIdList.isEmpty()) {
            return 0;
        }
        for (Long id : aiLlmVendorIdList) {
            assertDeletable(id);
            modelDiscoveryService.invalidateDiscoverCache(id);
        }
        return aiLlmVendorMapper.logicDeleteAiLlmVendorByIdList(aiLlmVendorIdList);
    }

    /** 内置厂商（builtin_status=1）不可删除 */
    private void assertDeletable(Long aiLlmVendorId) {
        AiLlmVendor row = aiLlmVendorMapper.selectAiLlmVendorById(aiLlmVendorId);
        if (row != null && row.getBuiltinStatus() != null && row.getBuiltinStatus() == 1) {
            throw new ServiceException("内置配置不可删除，可改为禁用");
        }
    }

    /** 判断前端传入的 apiKey 是否为脱敏占位，若是则保留库内原密钥 */
    private boolean isMaskedApiKey(String apiKey) {
        return apiKey == null || apiKey.isBlank() || MASKED_API_KEY.equals(apiKey);
    }

    /**
     * 查询AI 厂商数量
     *
     * @param params AI 厂商Params
     * @return 数量
     */
    @Override
    public int selectAiLlmVendorCount(AiLlmVendorParams params) {
        return aiLlmVendorMapper.selectAiLlmVendorCount(params);
    }

    /**
     * 按条件查询单条AI 厂商
     *
     * @param params AI 厂商Params
     * @return AI 厂商
     */
    @Override
    public AiLlmVendor selectAiLlmVendorOne(AiLlmVendorParams params) {
        return aiLlmVendorMapper.selectAiLlmVendorOne(params);
    }
}
