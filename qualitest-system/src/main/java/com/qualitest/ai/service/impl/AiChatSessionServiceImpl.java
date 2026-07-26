package com.qualitest.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.ai.mapper.AiChatSessionMapper;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.params.AiChatSessionParams;
import com.qualitest.ai.result.AiChatSessionResult;
import com.qualitest.ai.service.IAiChatSessionService;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 会话Service业务层处理
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Service
public class AiChatSessionServiceImpl implements IAiChatSessionService {
    @Autowired
    private AiChatSessionMapper aiChatSessionMapper;

    /**
     * 查询AI 会话列表
     *
     * @param aiChatSession AI 会话
     * @return AI 会话
     */
    @Override
    public List<AiChatSession> selectAiChatSessionList(AiChatSession aiChatSession) {
        return aiChatSessionMapper.selectAiChatSessionList(aiChatSession);
    }

    /**
     * 查询AI 会话
     *
     * @param aiChatSessionId AI 会话主键
     * @return AI 会话
     */
    @Override
    public AiChatSession selectAiChatSessionById(Long aiChatSessionId) {
        return aiChatSessionMapper.selectAiChatSessionById(aiChatSessionId);
    }

    /**
     * 查询AI 会话Result列表
     *
     * @param params AI 会话Params
     * @return AI 会话Result集合
     */
    @Override
    public List<AiChatSessionResult> selectAiChatSessionResultList(AiChatSessionParams params) {
        return aiChatSessionMapper.selectAiChatSessionResultList(params);
    }

    /**
     * 获取AI 会话详细信息
     *
     * @param aiChatSessionId AI 会话主键
     * @return AI 会话Result
     */
    @Override
    public AiChatSessionResult selectAiChatSessionResult(Long aiChatSessionId) {
        return aiChatSessionMapper.selectAiChatSessionResult(aiChatSessionId);
    }

    /**
     * 新增AI 会话
     *
     * @param aiChatSession AI 会话
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertAiChatSession(AiChatSession aiChatSession) {
        if (Objects.isNull(aiChatSession.getAiChatSessionId())) {
            aiChatSession.setAiChatSessionId(IdUtil.getSnowflakeNextId());
        }
        aiChatSession.setCreateTime(DateUtils.getNowDate());
        if (aiChatSession.getDelStatus() == null) {
            aiChatSession.setDelStatus(0);
        }
        return aiChatSessionMapper.insertAiChatSession(aiChatSession);
    }

    /**
     * 修改AI 会话
     *
     * @param aiChatSession AI 会话
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateAiChatSession(AiChatSession aiChatSession) {
        return aiChatSessionMapper.updateAiChatSession(aiChatSession);
    }

    /**
     * 批量删除AI 会话
     * 
     * @param aiChatSessionIdList 需要删除的AI 会话主键集合
     * @return 结果
     */
    @Override
    public int deleteAiChatSessionByIdList(List<Long> aiChatSessionIdList) {
        return aiChatSessionMapper.deleteAiChatSessionByIdList(aiChatSessionIdList);
    }

    /**
     * 删除AI 会话信息
     * 
     * @param aiChatSessionId AI 会话主键
     * @return 结果
     */
    @Override
    public int deleteAiChatSessionById(Long aiChatSessionId) {
        return aiChatSessionMapper.deleteAiChatSessionById(aiChatSessionId);
    }

    /**
     * 查询AI 会话数量
     *
     * @param params AI 会话Params
     * @return 数量
     */
    @Override
    public int selectAiChatSessionCount(AiChatSessionParams params) {
        return aiChatSessionMapper.selectAiChatSessionCount(params);
    }

    /**
     * 按条件查询单条AI 会话
     *
     * @param params AI 会话Params
     * @return AI 会话
     */
    @Override
    public AiChatSession selectAiChatSessionOne(AiChatSessionParams params) {
        return aiChatSessionMapper.selectAiChatSessionOne(params);
    }
}
