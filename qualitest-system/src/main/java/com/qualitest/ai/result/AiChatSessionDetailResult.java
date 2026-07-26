package com.qualitest.ai.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 画布 AI 会话详情：会话元数据 + 按时间升序的消息列表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSessionDetailResult implements Serializable {

    private AiChatSessionResult session;

    @Builder.Default
    private List<AiChatMessageResult> messages = new ArrayList<>();

    /** 会话消息总数（分页请求时返回） */
    private Integer totalMessageCount;

    /** 是否还有更早消息可加载（分页请求时返回） */
    private Boolean hasMoreOlder;
}
