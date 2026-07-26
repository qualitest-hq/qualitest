package com.qualitest.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * AI 会话消息对象 ai_chat_message
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiChatMessage")
public class AiChatMessage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatMessageId;

    /**
     * 会话ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatSessionId;

    /**
     * 消息角色（system系统 user用户 assistant模型）
     */
    private String messageRole;

    /**
     * 模型ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 消息内容
     */
    private String messageContent;

    /**
     * 思考内容（仅 assistant，展示用，不送入多轮 LLM）
     */
    private String thinkingContent;

    /**
     * 结果摘要
     */
    private String resultMetaJson;

}
