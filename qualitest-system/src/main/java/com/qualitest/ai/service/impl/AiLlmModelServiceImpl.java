package com.qualitest.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.ai.result.*;
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
import com.qualitest.ai.llm.template.ProviderTemplate;
import com.qualitest.ai.llm.template.ProviderTemplateRegistry;
import com.qualitest.ai.service.IAiLlmModelService;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 模型业务实现：表 CRUD、按主键解析运行时配置、按厂商分组列出启用模型。
 */
@Service
public class AiLlmModelServiceImpl implements IAiLlmModelService {
    @Autowired
    private AiLlmModelMapper aiLlmModelMapper;

    @Autowired
    private AiLlmConfigService aiLlmConfigService;

    /** 厂商模板：提供思考请求风格 thinkingControl */
    @Autowired
    private ProviderTemplateRegistry providerTemplateRegistry;

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
     * 批量物理删除模型（含内置；过期模型可删后重新获取）。
     *
     * @param aiLlmModelIdList 需要删除的 AI 模型主键集合
     * @return 结果
     */
    @Override
    public int deleteAiLlmModelByIdList(List<Long> aiLlmModelIdList) {
        if (aiLlmModelIdList == null || aiLlmModelIdList.isEmpty()) {
            return 0;
        }
        return aiLlmModelMapper.deleteAiLlmModelByIdList(aiLlmModelIdList);
    }

    /**
     * 物理删除模型（含内置；过期模型可删后重新获取）。
     *
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    @Override
    public int deleteAiLlmModelById(Long aiLlmModelId) {
        return aiLlmModelMapper.deleteAiLlmModelById(aiLlmModelId);
    }

    /**
     * 逻辑删除模型（含内置；过期模型可删后重新获取）。
     *
     * @param aiLlmModelId AI 模型主键
     * @return 结果
     */
    @Override
    public int logicDeleteAiLlmModelById(Long aiLlmModelId) {
        return aiLlmModelMapper.logicDeleteAiLlmModelById(aiLlmModelId);
    }

    /**
     * 批量逻辑删除模型（含内置；过期模型可删后重新获取）。
     *
     * @param aiLlmModelIdList AI 模型主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteAiLlmModelByIdList(List<Long> aiLlmModelIdList) {
        return aiLlmModelMapper.logicDeleteAiLlmModelByIdList(aiLlmModelIdList);
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
     * 按模型主键解析运行时配置：校验启用与密钥，JOIN 厂商得到 baseUrl/apiKey/协议，
     * 并从元数据目录按 model 名补上思考请求风格。
     *
     * @param aiLlmModelId 模型主键
     * @return 可直接发起调用的配置
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
        // 仅接受已实现的两种协议，避免调用时才发现配置错误
        if (!LlmProviderTypes.isOpenAiCompatible(row.getProvider())
                && !LlmProviderTypes.isAnthropic(row.getProvider())) {
            throw new LlmClientException("不支持的协议标识：" + row.getProvider());
        }
        // 思考请求风格来自厂商模板，不落库
        ProviderTemplate template = row.getTemplateId() != null
                ? providerTemplateRegistry.getById(row.getTemplateId()) : null;
        String thinkingControl = template != null ? template.getThinkingControl() : null;
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
                .thinkingControl(thinkingControl)
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
