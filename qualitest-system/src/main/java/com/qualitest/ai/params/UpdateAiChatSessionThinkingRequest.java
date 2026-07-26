package com.qualitest.ai.params;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新会话思考开关。
 */
@Getter
@Setter
public class UpdateAiChatSessionThinkingRequest {

    /** true 开启，false 关闭 */
    private Boolean thinkingEnabled;
}
