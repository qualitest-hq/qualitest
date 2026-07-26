package com.qualitest.ai.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * AI 会话消息 Params 对象
 *
 * @author qualitest
 * @since 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiChatMessageParams")
public class AiChatMessageParams extends BaseEntity implements Serializable {

    /**
     * 会话ID
     */
    private Long aiChatSessionId;

    /**
     * 消息角色（system系统 user用户 assistant模型）
     */
    private String messageRole;

    /**
     * 模型ID
     */
    private Long aiLlmModelId;

}
