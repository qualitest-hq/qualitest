package com.qualitest.ai.service;

import java.util.List;
import com.qualitest.ai.domain.AiChatMessage;
import com.qualitest.ai.params.AiChatMessageParams;
import com.qualitest.ai.result.AiChatMessageResult;

/**
 * AI 会话消息Service接口
 * 
 * @author qualitest
 * @date 2026-06-15
 */
public interface IAiChatMessageService {
    /**
     * 查询AI 会话消息列表
     *
     * @param aiChatMessage AI 会话消息
     * @return AI 会话消息集合
     */
    List<AiChatMessage> selectAiChatMessageList(AiChatMessage aiChatMessage);

    /**
     * 查询AI 会话消息
     *
     * @param aiChatMessageId AI 会话消息主键
     * @return AI 会话消息
     */
    AiChatMessage selectAiChatMessageById(Long aiChatMessageId);

    /**
     * 查询AI 会话消息Result列表
     *
     * @param params AI 会话消息Params
     * @return AI 会话消息Result集合
     */
    List<AiChatMessageResult> selectAiChatMessageResultList(AiChatMessageParams params);

    /**
     * 获取AI 会话消息详细信息
     *
     * @param aiChatMessageId AI 会话消息主键
     * @return AI 会话消息Result
     */
    AiChatMessageResult selectAiChatMessageResult(Long aiChatMessageId);

    /**
     * 新增AI 会话消息
     * 
     * @param aiChatMessage AI 会话消息
     * @return 结果
     */
    int insertAiChatMessage(AiChatMessage aiChatMessage);

    /**
     * 修改AI 会话消息
     * 
     * @param aiChatMessage AI 会话消息
     * @return 结果
     */
    int updateAiChatMessage(AiChatMessage aiChatMessage);

    /**
     * 批量删除AI 会话消息
     * 
     * @param aiChatMessageIdList 需要删除的AI 会话消息主键集合
     * @return 结果
     */
    int deleteAiChatMessageByIdList(List<Long> aiChatMessageIdList);

    /**
     * 删除AI 会话消息信息
     * 
     * @param aiChatMessageId AI 会话消息主键
     * @return 结果
     */
    public int deleteAiChatMessageById(Long aiChatMessageId);

    /**
     * 查询AI 会话消息数量
     *
     * @param params AI 会话消息Params
     * @return 数量
     */
    int selectAiChatMessageCount(AiChatMessageParams params);

    /**
     * 按条件查询单条AI 会话消息
     *
     * @param params AI 会话消息Params
     * @return AI 会话消息
     */
    AiChatMessage selectAiChatMessageOne(AiChatMessageParams params);
}
