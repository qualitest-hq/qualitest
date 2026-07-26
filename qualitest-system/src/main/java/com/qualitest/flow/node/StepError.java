package com.qualitest.flow.node;

import com.qualitest.flow.exception.FlowErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 步骤失败信息，对应 {@code step_details.error}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepError {

    /** {@code TF_*} 错误码 */
    private String code;

    /** 可读错误描述 */
    private String message;

    /**
     * 由 {@link FlowErrorCode} 构建步骤错误。
     */
    public static StepError of(FlowErrorCode errorCode, String message) {
        return StepError.builder()
                .code(errorCode.getCode())
                .message(message)
                .build();
    }
}
