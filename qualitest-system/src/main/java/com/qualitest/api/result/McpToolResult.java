package com.qualitest.api.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * MCP 工具执行成功后的结构化结果。
 * <p>
 * {@link #resultJson} 为底层 Tool 返回的完整 JSON 字符串，协议层将其作为
 * {@code tools/call} 响应中 {@code content[].text} 的正文交给 IDE。
 * 若结果被截断，{@code truncated} 等标记保留在 resultJson 内嵌字段中。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolResult implements Serializable {

    /** 本次执行的工具名。 */
    private String tool;

    /** 工具业务输出，JSON 格式字符串；调用失败时通过异常返回，不构造本对象。 */
    private String resultJson;

    /** 业务层是否失败（resultJson 含 error 或协议层 ServiceException）。 */
    private boolean error;
}
