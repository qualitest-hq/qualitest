package com.qualitest.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.params.AiChatSessionParams;
import com.qualitest.ai.result.AiChatSessionResult;

/**
 * AI 会话Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Mapper
public interface AiChatSessionMapper {
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
     * 删除AI 会话
     * 
     * @param aiChatSessionId AI 会话主键
     * @return 结果
     */
    int deleteAiChatSessionById(Long aiChatSessionId);

    /**
     * 批量删除AI 会话
     * 
     * @param aiChatSessionIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAiChatSessionByIdList(@Param("list") List<Long> aiChatSessionIdList);
}
