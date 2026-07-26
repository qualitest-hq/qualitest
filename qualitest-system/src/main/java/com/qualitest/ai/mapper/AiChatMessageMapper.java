package com.qualitest.ai.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.ai.domain.AiChatMessage;
import com.qualitest.ai.params.AiChatMessageParams;
import com.qualitest.ai.result.AiChatMessageResult;

/**
 * AI 会话消息Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Mapper
public interface AiChatMessageMapper {
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
     * 删除AI 会话消息
     * 
     * @param aiChatMessageId AI 会话消息主键
     * @return 结果
     */
    int deleteAiChatMessageById(Long aiChatMessageId);

    /**
     * 批量删除AI 会话消息
     * 
     * @param aiChatMessageIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteAiChatMessageByIdList(@Param("list") List<Long> aiChatMessageIdList);

    /**
     * 按会话 id 删除全部消息
     */
    int deleteAiChatMessageBySessionId(@Param("aiChatSessionId") Long aiChatSessionId);

    /**
     * 按会话 id 查询消息，按创建时间升序
     */
    List<AiChatMessageResult> selectAiChatMessageResultListBySessionAsc(@Param("aiChatSessionId") Long aiChatSessionId);

    /**
     * 按会话分页查询消息（升序）。beforeMessageId 为空时取最新 limit 条；否则取该 id 之前的 limit 条更早消息。
     */
    List<AiChatMessageResult> selectAiChatMessageResultPageBySession(
            @Param("aiChatSessionId") Long aiChatSessionId,
            @Param("beforeMessageId") Long beforeMessageId,
            @Param("limit") int limit);

    /** 统计会话消息总数 */
    int countAiChatMessageBySessionId(@Param("aiChatSessionId") Long aiChatSessionId);

    /** 统计早于指定消息 id 的消息数（用于 hasMoreOlder） */
    int countAiChatMessageBeforeId(
            @Param("aiChatSessionId") Long aiChatSessionId,
            @Param("beforeMessageId") Long beforeMessageId);
}
