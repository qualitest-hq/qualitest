package com.qualitest.flow.context;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 单次 Run 运行时上下文。
 * <p>
 * 持有 env / flow / asset / session、外联权限、项目 ID，
 * 以及项目多端配置（projectAuthConfig：鉴权头 + 各端响应约定）。
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
     * 最近一步 HTTP 响应快照（status / headers / body / durationMs /
     * 实际响应形态 responseKind / 是否符合接口期望 expectedMatch）
     */
    private HttpResponseSnapshot lastResponse;

    /**
     * 当前 Run 所属测试项目 ID（子流归属校验）
     */
    private Long testProjectId;

    /**
     * 项目多端配置原始 JSON（表字段 auth_config）。
     * HTTP 节点发送前按接口鉴权标签解析托管头；业务码校验按 path 命中 Profile 的响应约定。
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
        /**
         * 实际响应形态：json（对象/数组）或 nonJson（HTML、纯文本等）
         */
        private String responseKind;
        /**
         * 实际响应是否符合接口配置的期望形态；无绑定接口时为 true
         */
        private Boolean expectedMatch;
    }
}
