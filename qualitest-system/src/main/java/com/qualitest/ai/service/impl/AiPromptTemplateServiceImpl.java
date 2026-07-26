package com.qualitest.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.ai.domain.AiPromptTemplate;
import com.qualitest.ai.mapper.AiPromptTemplateMapper;
import com.qualitest.ai.params.AiPromptTemplateParams;
import com.qualitest.ai.result.AiPromptTemplateResult;
import com.qualitest.ai.service.IAiPromptTemplateService;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * AI提示词模板Service业务层处理
 * 
 * @author qualitest
 * @date 2026-07-04
 */
@Service
public class AiPromptTemplateServiceImpl implements IAiPromptTemplateService {
    private static final String SCOPE_PLATFORM = "platform";
    private static final String SCOPE_PROJECT = "project";

    @Autowired
    private AiPromptTemplateMapper aiPromptTemplateMapper;

    /**
     * 查询AI提示词模板列表
     *
     * @param aiPromptTemplate AI提示词模板
     * @return AI提示词模板
     */
    @Override
    public List<AiPromptTemplate> selectAiPromptTemplateList(AiPromptTemplate aiPromptTemplate) {
        return aiPromptTemplateMapper.selectAiPromptTemplateList(aiPromptTemplate);
    }

    /**
     * 查询AI提示词模板
     *
     * @param aiPromptTemplateId AI提示词模板主键
     * @return AI提示词模板
     */
    @Override
    public AiPromptTemplate selectAiPromptTemplateById(Long aiPromptTemplateId) {
        return aiPromptTemplateMapper.selectAiPromptTemplateById(aiPromptTemplateId);
    }

    /**
     * 查询AI提示词模板Result列表
     *
     * @param params AI提示词模板Params
     * @return AI提示词模板Result集合
     */
    @Override
    public List<AiPromptTemplateResult> selectAiPromptTemplateResultList(AiPromptTemplateParams params) {
        return aiPromptTemplateMapper.selectAiPromptTemplateResultList(params);
    }

    /**
     * 获取AI提示词模板详细信息
     *
     * @param aiPromptTemplateId AI提示词模板主键
     * @return AI提示词模板Result
     */
    @Override
    public AiPromptTemplateResult selectAiPromptTemplateResult(Long aiPromptTemplateId) {
        return aiPromptTemplateMapper.selectAiPromptTemplateResult(aiPromptTemplateId);
    }

    /**
     * 新增AI提示词模板
     *
     * @param aiPromptTemplate AI提示词模板
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertAiPromptTemplate(AiPromptTemplate aiPromptTemplate) {
        validateScope(aiPromptTemplate);
        if (Objects.isNull(aiPromptTemplate.getAiPromptTemplateId())) {
            aiPromptTemplate.setAiPromptTemplateId(IdUtil.getSnowflakeNextId());
        }
        if (aiPromptTemplate.getBuiltinStatus() == null) {
            aiPromptTemplate.setBuiltinStatus(0);
        }
        if (aiPromptTemplate.getBuiltinStatus() == 1 && !SCOPE_PLATFORM.equals(aiPromptTemplate.getTemplateScope())) {
            throw new ServiceException("仅平台级模板可标记为内置");
        }
        aiPromptTemplate.setCreateTime(DateUtils.getNowDate());
        return aiPromptTemplateMapper.insertAiPromptTemplate(aiPromptTemplate);
    }

    /**
     * 修改AI提示词模板
     *
     * @param aiPromptTemplate AI提示词模板
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateAiPromptTemplate(AiPromptTemplate aiPromptTemplate) {
        AiPromptTemplate existing = aiPromptTemplateMapper.selectAiPromptTemplateById(aiPromptTemplate.getAiPromptTemplateId());
        if (existing == null) {
            throw new ServiceException("模板不存在");
        }
        if (isBuiltin(existing)) {
            if (aiPromptTemplate.getTemplateScope() != null
                    && !SCOPE_PLATFORM.equals(aiPromptTemplate.getTemplateScope())) {
                throw new ServiceException("内置模板不可修改范围");
            }
            if (aiPromptTemplate.getBuiltinStatus() != null && aiPromptTemplate.getBuiltinStatus() != 1) {
                throw new ServiceException("内置模板不可取消内置标记");
            }
        }
        validateScope(aiPromptTemplate);
        aiPromptTemplate.setUpdateTime(DateUtils.getNowDate());
        return aiPromptTemplateMapper.updateAiPromptTemplate(aiPromptTemplate);
    }

    /**
     * 批量删除AI提示词模板
     * 
     * @param aiPromptTemplateIdList 需要删除的AI提示词模板主键集合
     * @return 结果
     */
    @Override
    public int deleteAiPromptTemplateByIdList(List<Long> aiPromptTemplateIdList) {
        if (aiPromptTemplateIdList == null || aiPromptTemplateIdList.isEmpty()) {
            return 0;
        }
        for (Long id : aiPromptTemplateIdList) {
            assertDeletable(id);
        }
        return aiPromptTemplateMapper.deleteAiPromptTemplateByIdList(aiPromptTemplateIdList);
    }

    /**
     * 删除AI提示词模板信息
     * 
     * @param aiPromptTemplateId AI提示词模板主键
     * @return 结果
     */
    @Override
    public int deleteAiPromptTemplateById(Long aiPromptTemplateId) {
        assertDeletable(aiPromptTemplateId);
        return aiPromptTemplateMapper.deleteAiPromptTemplateById(aiPromptTemplateId);
    }

    /**
     * 逻辑删除AI提示词模板信息
     * 
     * @param aiPromptTemplateId AI提示词模板主键
     * @return 结果
     */
    @Override
    public int logicDeleteAiPromptTemplateById(Long aiPromptTemplateId) {
        assertDeletable(aiPromptTemplateId);
        return aiPromptTemplateMapper.logicDeleteAiPromptTemplateById(aiPromptTemplateId);
    }

    /**
     * 批量逻辑删除AI提示词模板信息
     * 
     * @param aiPromptTemplateIdList AI提示词模板主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteAiPromptTemplateByIdList(List<Long> aiPromptTemplateIdList) {
        for (Long id : aiPromptTemplateIdList) {
            assertDeletable(id);
        }
        return aiPromptTemplateMapper.logicDeleteAiPromptTemplateByIdList(aiPromptTemplateIdList);
    }

    /**
     * 查询AI提示词模板数量
     *
     * @param params AI提示词模板Params
     * @return 数量
     */
    @Override
    public int selectAiPromptTemplateCount(AiPromptTemplateParams params) {
        return aiPromptTemplateMapper.selectAiPromptTemplateCount(params);
    }

    /**
     * 按条件查询单条AI提示词模板
     *
     * @param params AI提示词模板Params
     * @return AI提示词模板
     */
    @Override
    public AiPromptTemplate selectAiPromptTemplateOne(AiPromptTemplateParams params) {
        return aiPromptTemplateMapper.selectAiPromptTemplateOne(params);
    }

    @Override
    public List<AiPromptTemplateResult> listForDesignPanel(Long testProjectId, String sessionScene) {
        String scene = StrUtil.blankToDefault(sessionScene, AiChatConversationService.SCENE_TEST_FLOW_DESIGN);
        return aiPromptTemplateMapper.selectAiPromptTemplateForDesignPanel(testProjectId, scene);
    }

    private void validateScope(AiPromptTemplate aiPromptTemplate) {
        if (aiPromptTemplate == null || StrUtil.isBlank(aiPromptTemplate.getTemplateScope())) {
            return;
        }
        if (SCOPE_PROJECT.equals(aiPromptTemplate.getTemplateScope()) && aiPromptTemplate.getTestProjectId() == null) {
            throw new ServiceException("项目级模板必须指定测试项目");
        }
        if (SCOPE_PLATFORM.equals(aiPromptTemplate.getTemplateScope()) && aiPromptTemplate.getTestProjectId() != null) {
            throw new ServiceException("平台级模板不可绑定测试项目");
        }
    }

    private void assertDeletable(Long aiPromptTemplateId) {
        AiPromptTemplate row = aiPromptTemplateMapper.selectAiPromptTemplateById(aiPromptTemplateId);
        if (isBuiltin(row)) {
            throw new ServiceException("内置模板不可删除，可改为禁用");
        }
    }

    private static boolean isBuiltin(AiPromptTemplate row) {
        return row != null && row.getBuiltinStatus() != null && row.getBuiltinStatus() == 1;
    }
}
