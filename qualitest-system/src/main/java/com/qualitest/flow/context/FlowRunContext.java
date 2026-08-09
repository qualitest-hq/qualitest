package com.qualitest.flow.context;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 单次 Run 运行时上下文。
 * <p>
 * 持有 env / flow / asset / session、Cookie Jar、外联权限、项目 ID，
 * 以及项目响应约定（responseConvention），供 HTTP 节点校验 body 业务码。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowRunContext {

    @Builder.Default
    private Map<String, Object> env = new HashMap<>();

    @Builder.Default
    private Map<String, Object> flow = new HashMap<>();

    /**
     * 素材 key → 业务值根节点（通常为 Map）
     */
    @Builder.Default
    private Map<String, Object> asset = new HashMap<>();

    /**
     * Run 级会话存储（token 缓存等），子流与主流程共享同一 Run 的 session。
     */
    @Builder.Default
    private Map<String, Object> session = new HashMap<>();

    /**
     * Run 级 Cookie Jar，多步 HTTP 共享（useRunSession）。
     */
    @Builder.Default
    private com.qualitest.flow.session.FlowRunSession runSession = new com.qualitest.flow.session.FlowRunSession();

    /**
     * 当前执行所处的子流深度：主流程 0，每进入一层子流 +1。
     */
    @Builder.Default
    private int subflowDepth = 0;

    /**
     * 当前 Run 是否允许执行外联 HTTP（由触发 Run 时按成员角色/权限写入）。
     */
    @Builder.Default
    private boolean externalHttpPermitted = true;

    /**
     * 最近一步 HTTP 响应快照（含 status / headers / body / durationMs）
     */
    private HttpResponseSnapshot lastResponse;

    /**
     * 当前 Run 所属测试项目 ID（子流归属校验）
     */
    private Long testProjectId;

    /**
     * 项目响应约定原始 JSON（表字段 response_convention）。
     * HTTP 节点校验业务码时读取；为空则按默认约定（code / [200] / msg / data）解析。
     */
    private String responseConvention;

    /**
     * 项目鉴权配置原始 JSON（表字段 auth_config）。
     * HTTP 节点发送前按接口鉴权标签解析是否补 Authorization 等托管头。
     */
    private String projectAuthConfig;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpResponseSnapshot {
        private int status;
        private Map<String, Object> headers;
        private Object body;
        /**
         * 该步 HTTP 请求耗时（毫秒）
         */
        private Long durationMs;
    }
}
