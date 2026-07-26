package com.qualitest.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.ai.mapper.AiChatMessageMapper;
import com.qualitest.ai.domain.AiChatMessage;
import com.qualitest.ai.params.AiChatMessageParams;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.service.IAiChatMessageService;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 会话消息Service业务层处理
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Service
public class AiChatMessageServiceImpl implements IAiChatMessageService {
    @Autowired
    private AiChatMessageMapper aiChatMessageMapper;

    /**
     * 查询AI 会话消息列表
     *
     * @param aiChatMessage AI 会话消息
     * @return AI 会话消息
     */
    @Override
    public List<AiChatMessage> selectAiChatMessageList(AiChatMessage aiChatMessage) {
        return aiChatMessageMapper.selectAiChatMessageList(aiChatMessage);
    }

    /**
     * 查询AI 会话消息
     *
     * @param aiChatMessageId AI 会话消息主键
     * @return AI 会话消息
     */
    @Override
    public AiChatMessage selectAiChatMessageById(Long aiChatMessageId) {
        return aiChatMessageMapper.selectAiChatMessageById(aiChatMessageId);
    }

    /**
     * 查询AI 会话消息Result列表
     *
     * @param params AI 会话消息Params
     * @return AI 会话消息Result集合
     */
    @Override
    public List<AiChatMessageResult> selectAiChatMessageResultList(AiChatMessageParams params) {
        return aiChatMessageMapper.selectAiChatMessageResultList(params);
    }

    /**
     * 获取AI 会话消息详细信息
     *
     * @param aiChatMessageId AI 会话消息主键
     * @return AI 会话消息Result
     */
    @Override
    public AiChatMessageResult selectAiChatMessageResult(Long aiChatMessageId) {
        return aiChatMessageMapper.selectAiChatMessageResult(aiChatMessageId);
    }

    /**
     * 新增AI 会话消息
     *
     * @param aiChatMessage AI 会话消息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertAiChatMessage(AiChatMessage aiChatMessage) {
        if (Objects.isNull(aiChatMessage.getAiChatMessageId())) {
            aiChatMessage.setAiChatMessageId(IdUtil.getSnowflakeNextId());
        }
        aiChatMessage.setCreateTime(DateUtils.getNowDate());
        return aiChatMessageMapper.insertAiChatMessage(aiChatMessage);
    }

    /**
     * 修改AI 会话消息
     *
     * @param aiChatMessage AI 会话消息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateAiChatMessage(AiChatMessage aiChatMessage) {
        return aiChatMessageMapper.updateAiChatMessage(aiChatMessage);
    }

    /**
     * 批量删除AI 会话消息
     * 
     * @param aiChatMessageIdList 需要删除的AI 会话消息主键集合
     * @return 结果
     */
    @Override
    public int deleteAiChatMessageByIdList(List<Long> aiChatMessageIdList) {
        return aiChatMessageMapper.deleteAiChatMessageByIdList(aiChatMessageIdList);
    }

    /**
     * 删除AI 会话消息信息
     * 
     * @param aiChatMessageId AI 会话消息主键
     * @return 结果
     */
    @Override
    public int deleteAiChatMessageById(Long aiChatMessageId) {
        return aiChatMessageMapper.deleteAiChatMessageById(aiChatMessageId);
    }

    /**
     * 查询AI 会话消息数量
     *
     * @param params AI 会话消息Params
     * @return 数量
     */
    @Override
    public int selectAiChatMessageCount(AiChatMessageParams params) {
        return aiChatMessageMapper.selectAiChatMessageCount(params);
    }

    /**
     * 按条件查询单条AI 会话消息
     *
     * @param params AI 会话消息Params
     * @return AI 会话消息
     */
    @Override
    public AiChatMessage selectAiChatMessageOne(AiChatMessageParams params) {
        return aiChatMessageMapper.selectAiChatMessageOne(params);
    }
}
