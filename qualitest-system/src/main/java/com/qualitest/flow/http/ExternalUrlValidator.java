package com.qualitest.flow.http;

import com.qualitest.api.util.HttpEgressAllowlist;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;

/**
 * 测试流外联 HTTP 节点的 URL 基础校验。
 * <p>
 * 在占位符解析得到完整 URL 后调用：要求协议为 http/https，且含非空主机名。
 * 不在此处做 host:port 白名单；白名单在实际转发前另行校验。
 * 失败时抛出 {@code TF_HTTP_EXTERNAL_DENIED}。
 */
public final class ExternalUrlValidator {

    private ExternalUrlValidator() {
    }

    /**
     * 校验外联用完整 URL 的协议与主机。
     *
     * @param resolvedUrl 占位符已解析的完整 URL
     * @throws FlowExecutionException 协议非法、缺主机或 URL 为空/格式错误时
     */
    public static void validate(String resolvedUrl) {
        String err = HttpEgressAllowlist.validateSchemeAndHost(resolvedUrl);
        if (err != null) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, err);
        }
    }
}
