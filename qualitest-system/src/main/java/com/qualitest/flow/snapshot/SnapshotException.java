package com.qualitest.flow.snapshot;

import com.qualitest.flow.exception.FlowErrorCode;
import lombok.Getter;

/**
 * 调用被测系统 snapshot 或 restore 失败时抛出。
 * <p>
 * 携带质衡侧错误码；若被测方返回结构化错误，remoteCode 为其 code 字段。
 */
@Getter
public class SnapshotException extends RuntimeException {

    private final FlowErrorCode errorCode;

    /** 被测方错误响应中的 code，可能为空 */
    private final String remoteCode;

    public SnapshotException(FlowErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.remoteCode = null;
    }

    public SnapshotException(FlowErrorCode errorCode, String remoteCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.remoteCode = remoteCode;
    }
}
