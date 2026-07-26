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
import java.util.Date;

/**
 * AI 会话对象 ai_chat_session
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
@Alias("AiChatSession")
public class AiChatSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatSessionId;

    /**
     * 项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 用户ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 会话场景
     */
    private String sessionScene;

    /**
     * 业务锚点
     */
    private String bizRefJson;

    /**
     * 当前模型ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long currentModelId;

    /**
     * 思考开关（0关 1开 NULL跟随模型默认）
     */
    private Integer thinkingEnabled;

    /**
     * 会话标题
     */
    private String sessionTitle;

    /**
     * 滚动会话摘要（Checkpoint），供长会话保留早期决策
     */
    private String contextSummary;

    /**
     * 摘要已覆盖的消息条数
     */
    private Integer summaryMessageCount;

    /**
     * 摘要最近更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date summaryUpdatedAt;

    /**
     * 删除状态（0 正常 1 已删除）
     */
    private Integer delStatus;

}
