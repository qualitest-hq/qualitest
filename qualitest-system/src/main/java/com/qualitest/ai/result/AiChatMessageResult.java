package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * AI 会话消息 Result 对象
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
@Alias("AiChatMessageResult")
public class AiChatMessageResult implements Serializable {

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
    @Excel(name = "消息角色", readConverterExp = "system=系统,user=用户,assistant=模型")
    private String messageRole;

    /**
     * 模型ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 消息内容
     */
    @Excel(name = "消息内容")
    private String messageContent;

    /**
     * 思考内容
     */
    @Excel(name = "思考内容")
    private String thinkingContent;

    /**
     * 结果摘要
     */
    @Excel(name = "结果摘要")
    private String resultMetaJson;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;


}
