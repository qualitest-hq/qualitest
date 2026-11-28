package com.qualitest.flow.contract;

import java.util.Map;

/**
 * 子流入口（subflow-entry）专用默认值。
 * <p>
 * 子流兜底是业务对象，不是完整 HTTP 响应；引擎收束时再包成 {@code {body: …}} 挂到父流。
 */
public final class SubflowEntryDefaults {

    private SubflowEntryDefaults() {
    }

    /**
     * 子流入口新建或面板回退时的业务兜底对象（零次返回 code/message）。
     */
    public static Map<String, Object> defaultFallbackOut() {
        return FlowContractDefaults.defaultNoReturnBody();
    }
}
