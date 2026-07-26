package com.qualitest.flow.exception;

/**
 * 测试流执行异常（带 TF_* 错误码）
 */
public class FlowExecutionException extends RuntimeException {

    private final FlowErrorCode errorCode;
    private final String placeholder;

    public FlowExecutionException(FlowErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.placeholder = null;
    }

    public FlowExecutionException(FlowErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.placeholder = null;
    }

    public FlowExecutionException(FlowErrorCode errorCode, String message, String placeholder) {
        super(message);
        this.errorCode = errorCode;
        this.placeholder = placeholder;
    }

    public FlowErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
