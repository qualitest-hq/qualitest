package com.qualitest.flow.context;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 单次 Run 运行时上下文。
 * <p>
 * 持有 env / flow / asset / session、外联权限、项目 ID、
 * 最近一次 HTTP 快照 lastResponse、按节点 id 索引的 HTTP 快照 nodeResponses，
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
     * 本上下文内最近一次 HTTP 响应快照（status / headers / body / durationMs /
     * 实际响应形态 responseKind / 是否符合接口期望 expectedMatch）。
     * 中间非 HTTP 节点不会清空或改写本字段。
     */
    private HttpResponseSnapshot lastResponse;

    /**
     * 按画布节点 id 索引的 HTTP 响应快照。
     * 同一节点多次执行时覆盖为该节点最后一次响应；
     * 供路径 {@code nodes.<nodeId>.http…} 取值；暂停续跑序列化时不写入本 Map。
     */
    @Builder.Default
    private Map<String, HttpResponseSnapshot> nodeResponses = new HashMap<>();

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
        /** HTTP 状态码 */
        private int status;
        /** 响应头（键值均可访问） */
        private Map<String, Object> headers;
        /**
         * 解析后的响应体：JSON 对象/数组、XML 转成的对象，或未解析时的原文字符串；空体为 null
         */
        private Object body;
        /**
         * 响应原文（未解析的字符串）；XML 转对象后仍保留，便于取原报文
         */
        private String bodyText;
        /**
         * 该步 HTTP 请求耗时（毫秒）
         */
        private Long durationMs;
        /**
         * 实际响应形态：json（JSON 对象/数组）、xml（XML 已转对象）、nonJson（HTML、纯文本、解析失败等）
         */
        private String responseKind;
        /**
         * 实际形态是否符合接口配置的期望形态；无绑定接口时为 true
         */
        private Boolean expectedMatch;
    }

    /**
     * 同时更新最近一次 HTTP 快照与按节点 id 的索引。
     * nodeId 为空或空白时只写入 lastResponse，不写入 nodeResponses。
     *
     * @param nodeId   画布 HTTP 节点 id
     * @param snapshot 本步响应快照
     */
    public void putHttpResponse(String nodeId, HttpResponseSnapshot snapshot) {
        this.lastResponse = snapshot;
        if (nodeId == null || nodeId.isBlank() || snapshot == null) {
            return;
        }
        if (this.nodeResponses == null) {
            this.nodeResponses = new HashMap<>();
        }
        this.nodeResponses.put(nodeId.trim(), snapshot);
    }

    /**
     * 按画布节点 id 读取已索引的 HTTP 快照。
     * nodeId 为空、Map 未初始化或从未写入时返回 null。
     *
     * @param nodeId 画布节点 id
     * @return 该节点最后一次写入的快照，或 null
     */
    public HttpResponseSnapshot getNodeResponse(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || this.nodeResponses == null) {
            return null;
        }
        return this.nodeResponses.get(nodeId.trim());
    }
}
