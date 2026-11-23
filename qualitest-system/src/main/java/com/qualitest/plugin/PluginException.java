package com.qualitest.plugin;

/**
 * 平台插件启用、登记或调用失败时抛出的业务异常，携带原因码供接口原样返回。
 */
public class PluginException extends RuntimeException {

    private final PluginErrorCode errorCode;

    /**
     * @param errorCode 原因码
     * @param message   明细；空则用原因码默认文案
     */
    public PluginException(PluginErrorCode errorCode, String message) {
        super(message != null && !message.isBlank() ? message : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode 原因码
     * @param message   明细；空则用原因码默认文案
     * @param cause     底层异常
     */
    public PluginException(PluginErrorCode errorCode, String message, Throwable cause) {
        super(message != null && !message.isBlank() ? message : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    /** 机器可读原因码。 */
    public PluginErrorCode getErrorCode() {
        return errorCode;
    }
}
