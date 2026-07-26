package com.qualitest.ai.service;

import java.util.List;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.params.AiChatSessionParams;
import com.qualitest.ai.result.AiChatSessionResult;

/**
 * AI 会话Service接口
 * 
 * @author qualitest
 * @date 2026-06-15
 */
public interface IAiChatSessionService {
    /**
     * 查询AI 会话列表
     *
     * @param aiChatSession AI 会话
     * @return AI 会话集合
     */
    List<AiChatSession> selectAiChatSessionList(AiChatSession aiChatSession);

    /**
     * 查询AI 会话
     *
     * @param aiChatSessionId AI 会话主键
     * @return AI 会话
     */
    AiChatSession selectAiChatSessionById(Long aiChatSessionId);

    /**
     * 查询AI 会话Result列表
     *
     * @param params AI 会话Params
     * @return AI 会话Result集合
     */
    List<AiChatSessionResult> selectAiChatSessionResultList(AiChatSessionParams params);

    /**
     * 获取AI 会话详细信息
     *
     * @param aiChatSessionId AI 会话主键
     * @return AI 会话Result
     */
    AiChatSessionResult selectAiChatSessionResult(Long aiChatSessionId);

    /**
     * 新增AI 会话
     * 
     * @param aiChatSession AI 会话
     * @return 结果
     */
    int insertAiChatSession(AiChatSession aiChatSession);

    /**
     * 修改AI 会话
     * 
     * @param aiChatSession AI 会话
     * @return 结果
     */
    int updateAiChatSession(AiChatSession aiChatSession);

    /**
     * 批量删除AI 会话
     * 
     * @param aiChatSessionIdList 需要删除的AI 会话主键集合
     * @return 结果
     */
    int deleteAiChatSessionByIdList(List<Long> aiChatSessionIdList);

    /**
     * 删除AI 会话信息
     * 
     * @param aiChatSessionId AI 会话主键
     * @return 结果
     */
    public int deleteAiChatSessionById(Long aiChatSessionId);

    /**
     * 查询AI 会话数量
     *
     * @param params AI 会话Params
     * @return 数量
     */
    int selectAiChatSessionCount(AiChatSessionParams params);

    /**
     * 按条件查询单条AI 会话
     *
     * @param params AI 会话Params
     * @return AI 会话
     */
    AiChatSession selectAiChatSessionOne(AiChatSessionParams params);
}
