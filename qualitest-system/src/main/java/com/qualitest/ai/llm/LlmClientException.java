package com.qualitest.ai.llm;

/**
 * LLM 调用链路上的业务异常。
 * <p>
 * 涵盖配置缺失、HTTP 失败、响应解析失败、Agent 步数超限等；
 * Controller 层捕获后转为友好错误信息返回前端。
 */
public class LlmClientException extends RuntimeException {

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
