package com.qualitest.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.ai.result.*;
import com.qualitest.common.exception.ServiceException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.ai.mapper.AiLlmModelMapper;
import com.qualitest.ai.domain.AiLlmModel;
import com.qualitest.ai.params.AiLlmModelParams;
import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.LlmProviderTypes;
import com.qualitest.ai.service.IAiLlmModelService;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 模型业务实现。
 * <p>
 * CRUD 走 {@code ai_llm_model} 表；{@link #resolve(Long)} JOIN 厂商表组装 {@link LlmModelConfig}；
 * {@link #listModelsGrouped()} 仅返回启用且未删除的模型，按厂商 sort_num 分组。
 */
@Service
public class AiLlmModelServiceImpl implements IAiLlmModelService {
    @Autowired
    private AiLlmModelMapper aiLlmModelMapper;

    @Autowired
    private AiLlmConfigService aiLlmConfigService;

    /**
     * 查询AI 模型列表
     *
     * @param aiLlmModel AI 模型
     * @return AI 模型
     */
    @Override
    public List<AiLlmModel> selectAiLlmModelList(AiLlmModel aiLlmModel) {
        return aiLlmModelMapper.selectAiLlmModelList(aiLlmModel);
    }

    /**
     * 查询AI 模型
     *
     * @param aiLlmModelId AI 模型主键
     * @return AI 模型
     */
    @Override
    public AiLlmModel selectAiLlmModelById(Long aiLlmModelId) {
        return aiLlmModelMapper.selectAiLlmModelById(aiLlmModelId);
    }

    /**
     * 查询AI 模型Result列表
     *
     * @param params AI 模型Params
     * @return AI 模型Result集合
     */
    @Override
    public List<AiLlmModelResult> selectAiLlmModelResultList(AiLlmModelParams params) {
        return aiLlmModelMapper.selectAiLlmModelResultList(params);
    }

    /**
     * 获取AI 模型详细信息
     *
     * @param aiLlmModelId AI 模型主键
     * @return AI 模型Result
     */
    @Override
    public AiLlmModelResult selectAiLlmModelResult(Long aiLlmModelId) {
        return aiLlmModelMapper.selectAiLlmModelResult(aiLlmModelId);
    }

    /**
     * 新增AI 模型
     *
     * @param aiLlmModel AI 模型
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertAiLlmModel(AiLlmModel aiLlmModel) {
        if (Objects.isNull(aiLlmModel.getAiLlmModelId())) {
            aiLlmModel.setAiLlmModelId(IdUtil.getSnowflakeNextId());
        }
        aiLlmModel.setCreateTime(DateUtils.getNowDate());
        return aiLlmModelMapper.insertAiLlmModel(aiLlmModel);
    }

    /**
     * 修改AI 模型
     *
     * @param aiLlmModel AI 模型
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateAiLlmModel(AiLlmModel aiLlmModel) {
        aiLlmModel.setUpdateTime(DateUtils.getNowDate());
        return aiLlmModelMapper.updateAiLlmModel(aiLlmModel);
    }

    /**
     * 批量物理删除模型；内置模型（builtin_status=1）拒绝。
     *
     * @param aiLlmModelIdList 需要删除的 AI 模型主键集合
     * @return 结果
     */
    @Override
    public int deleteAiLlmModelByIdList(List<Long> aiLlmModelIdList) {
        if (aiLlmModelIdList == null || aiLlmModelIdList.isEmpty()) {
            return 0;
        }
        for (Long id : aiLlmModelIdList) {
            assertDeletable(id);
        }
        return aiLlmModelMapper.deleteAiLlmModelByIdList(aiLlmModelIdList);
    }

    /**
     * 物理删除模型；内置模型（builtin_status=1）拒绝。
     *
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    @Override
    public int deleteAiLlmModelById(Long aiLlmModelId) {
        assertDeletable(aiLlmModelId);
        return aiLlmModelMapper.deleteAiLlmModelById(aiLlmModelId);
    }

    /**
     * 逻辑删除模型；内置模型（builtin_status=1）拒绝。
     *
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    @Override
    public int logicDeleteAiLlmModelById(Long aiLlmModelId) {
        assertDeletable(aiLlmModelId);
        return aiLlmModelMapper.logicDeleteAiLlmModelById(aiLlmModelId);
    }

    /**
     * 批量逻辑删除模型；内置模型（builtin_status=1）拒绝。
     *
     * @param aiLlmModelIdList AI 模型主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteAiLlmModelByIdList(List<Long> aiLlmModelIdList) {
        for (Long id : aiLlmModelIdList) {
            assertDeletable(id);
        }
        return aiLlmModelMapper.logicDeleteAiLlmModelByIdList(aiLlmModelIdList);
    }

    /** 内置模型（builtin_status=1）不可删除 */
    private void assertDeletable(Long aiLlmModelId) {
        AiLlmModel row = aiLlmModelMapper.selectAiLlmModelById(aiLlmModelId);
        if (row != null && row.getBuiltinStatus() != null && row.getBuiltinStatus() == 1) {
            throw new ServiceException("内置配置不可删除，可改为禁用");
        }
    }

    /**
     * 查询AI 模型数量
     *
     * @param params AI 模型Params
     * @return 数量
     */
    @Override
    public int selectAiLlmModelCount(AiLlmModelParams params) {
        return aiLlmModelMapper.selectAiLlmModelCount(params);
    }

    /**
     * 按条件查询单条AI 模型
     *
     * @param params AI 模型Params
     * @return AI 模型
     */
    @Override
    public AiLlmModel selectAiLlmModelOne(AiLlmModelParams params) {
        return aiLlmModelMapper.selectAiLlmModelOne(params);
    }

    /**
     * 解析模型调用配置，校验启用状态与密钥完整性。
     */
    @Override
    public LlmModelConfig resolve(Long aiLlmModelId) {
        if (aiLlmModelId == null) {
            throw new LlmClientException("未指定 AI 模型");
        }
        AiLlmModelResolveResult row = aiLlmModelMapper.selectAiLlmModelResolve(aiLlmModelId);
        if (row == null) {
            throw new LlmClientException("AI 模型不存在：" + aiLlmModelId);
        }
        if (row.getModelDelStatus() != null && row.getModelDelStatus() != 0) {
            throw new LlmClientException("AI 模型已删除");
        }
        if (row.getVendorDelStatus() != null && row.getVendorDelStatus() != 0) {
            throw new LlmClientException("AI 厂商已删除");
        }
        if (row.getModelEnableStatus() == null || row.getModelEnableStatus() != 1) {
            throw new LlmClientException("AI 模型未启用");
        }
        if (row.getVendorEnableStatus() == null || row.getVendorEnableStatus() != 1) {
            throw new LlmClientException("AI 厂商未启用");
        }
        if (row.getApiKey() == null || row.getApiKey().isBlank()) {
            throw new LlmClientException("厂商 API Key 未配置");
        }
        if (row.getBaseUrl() == null || row.getBaseUrl().isBlank()) {
            throw new LlmClientException("厂商 Base URL 未配置");
        }
        // 运行时仅支持已实现的两种协议，避免调用阶段才暴露配置错误
        if (!LlmProviderTypes.isOpenAiCompatible(row.getProvider())
                && !LlmProviderTypes.isAnthropic(row.getProvider())) {
            throw new LlmClientException("不支持的协议标识：" + row.getProvider());
        }
        return LlmModelConfig.builder()
                .aiLlmModelId(row.getAiLlmModelId())
                .aiLlmVendorId(row.getAiLlmVendorId())
                .modelName(row.getModelName())
                .vendorName(row.getVendorName())
                .provider(row.getProvider())
                .baseUrl(row.getBaseUrl())
                .apiKey(row.getApiKey())
                .maxTokens(aiLlmConfigService.getMaxTokens())
                .connectTimeoutMs(aiLlmConfigService.getConnectTimeoutMs())
                .readTimeoutMs(aiLlmConfigService.getReadTimeoutMs())
                .writeTimeoutMs(aiLlmConfigService.getWriteTimeoutMs())
                .thinkingCapable(row.getThinkingCapable() != null && row.getThinkingCapable() == 1)
                .thinkingDefault(row.getThinkingDefault() != null && row.getThinkingDefault() == 1)
                .thinkingBudgetTokens(row.getThinkingBudgetTokens())
                .build();
    }

    /**
     * 按厂商分组返回启用模型；默认模型为厂商 sort_num、模型 sort_num 排序后的首项。
     */
    @Override
    public AiModelsListResult listModelsGrouped() {
        List<AiLlmModelResolveResult> rows = aiLlmModelMapper.selectEnabledAiLlmModelsWithVendor();
        Map<Long, AiModelVendorGroupResult> vendorMap = new LinkedHashMap<>();
        for (AiLlmModelResolveResult row : rows) {
            Long vendorId = row.getAiLlmVendorId();
            AiModelVendorGroupResult group = vendorMap.computeIfAbsent(vendorId, id -> AiModelVendorGroupResult.builder()
                    .aiLlmVendorId(id)
                    .vendorName(row.getVendorName())
                    .sortNum(row.getVendorSortNum())
                    .models(new ArrayList<>())
                    .build());
            group.getModels().add(AiModelOptionResult.builder()
                    .aiLlmModelId(row.getAiLlmModelId())
                    .modelName(row.getModelName())
                    .displayName(row.getDisplayName() != null ? row.getDisplayName() : row.getModelName())
                    .sortNum(row.getModelSortNum())
                    .thinkingCapable(row.getThinkingCapable() != null && row.getThinkingCapable() == 1)
                    .thinkingDefault(row.getThinkingDefault() != null && row.getThinkingDefault() == 1)
                    .build());
        }
        List<AiModelVendorGroupResult> vendors = new ArrayList<>(vendorMap.values());
        vendors.sort(Comparator.comparing(g -> g.getSortNum() != null ? g.getSortNum() : 0));
        for (AiModelVendorGroupResult group : vendors) {
            group.getModels().sort(Comparator.comparing(m -> m.getSortNum() != null ? m.getSortNum() : 0));
        }
        Long defaultModelId = null;
        if (!vendors.isEmpty() && !vendors.get(0).getModels().isEmpty()) {
            defaultModelId = vendors.get(0).getModels().get(0).getAiLlmModelId();
        }
        return AiModelsListResult.builder()
                .defaultModelId(defaultModelId)
                .vendors(vendors)
                .build();
    }
}
