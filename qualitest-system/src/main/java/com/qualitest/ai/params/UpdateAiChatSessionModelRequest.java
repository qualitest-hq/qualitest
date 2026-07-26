package com.qualitest.ai.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新会话当前模型请求体。
 */
@Getter
@Setter
public class UpdateAiChatSessionModelRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long currentModelId;
}
