package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * AI 会话 Result 对象
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
@Alias("AiChatSessionResult")
public class AiChatSessionResult implements Serializable {

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
    @Excel(name = "会话场景")
    private String sessionScene;

    /**
     * 业务锚点
     */
    @Excel(name = "业务锚点")
    private String bizRefJson;

    /**
     * 当前模型ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long currentModelId;

    /** 思考开关：0 关、1 开、null 跟随模型默认 */
    private Integer thinkingEnabled;

    /**
     * 会话标题
     */
    @Excel(name = "会话标题")
    private String sessionTitle;

    /**
     * 滚动会话摘要（Checkpoint）
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
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;


}
