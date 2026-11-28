package com.qualitest.flow.context;

import com.qualitest.flow.delivery.DeliverySink;
import lombok.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次 Run 运行时上下文。
 * <p>
 * 变量区：env / flow / asset / session；入参 {@code in}；出参 {@code out}（status/headers/body）。
 * 入口：entryKind（scenario/subflow/http 或协议入口短名）、entryNodeId、trigger（触发源）。
 * 交付：returnCount（http-return/subflow-return 成功次数）、delivered、deliveryWriteFailed、
 * deliverySink、inboundSlotToken。
 * 子流挂载：nodeOuts、parentContext、parentSubflowNodeId。
 * 权限与身份：externalHttpPermitted（外联 HTTP）、pausePermitted（Input 暂停）、
 * testProjectId / testProjectEnvId / testFlowRunId、projectAuthConfig。
 * HTTP 快照：lastResponse、nodeResponses；暂停落盘时一并序列化，续跑后仍可按节点读取。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowRunContext {

    /** 环境变量区，占位符路径 {@code env.*} */
    @Builder.Default
    private Map<String, Object> env = new HashMap<>();

    /** 流程变量区，占位符路径 {@code flow.*} */
    @Builder.Default
    private Map<String, Object> flow = new HashMap<>();

    /**
     * 运行时入参。
     * 开放 HTTP：常含 query / headers / body；子流：由父节点 inputs 解析灌入。
     * 占位符路径 {@code in.*}。
     */
    @Builder.Default
    private Map<String, Object> in = new LinkedHashMap<>();

    /**
     * 运行时出参，键为 status / headers / body。
     * Assign(scope=out) 写入；http-return / subflow-return 定稿后覆盖为交付包；占位符路径 {@code out.*}。
     */
    @Builder.Default
    private Map<String, Object> out = new LinkedHashMap<>();

    /**
     * 入口种类字符串：scenario / subflow / http，或协议入口的协议短名（如 tcp）。
     * 决定用哪种返回节点、是否对外交付、收束是否强制兜底；占位符路径 {@code run.entryKind}。
     */
    @Builder.Default
    private String entryKind = "scenario";

    /**
     * 本图实际入口节点 id。
     * 开放入站由路由写入；入口 Handler 在空时补写；收束读 fallbackOut 时用来定位入口。
     */
    private String entryNodeId;

    /**
     * 触发源字符串：ui / suite / http / mcp / subflow 等，表示谁发起了本次运行；
     * 与入口种类 entryKind 相互独立，占位符路径 {@code run.trigger}。
     */
    @Builder.Default
    private String trigger = "ui";

    /**
     * 本 Run 已成功执行返回节点（http-return / subflow-return）交付的次数。
     * 用于拦截整次第二次返回交付。
     */
    @Builder.Default
    private int returnCount = 0;

    /**
     * 是否已完成一次对外交付（开放写出或子流挂父）。
     * 为 true 后交付器与收束均不再写第二遍。
     */
    @Builder.Default
    private boolean delivered = false;

    /**
     * 开放 HTTP 写出失败（连接已断、DeferredResult 已过期等）。
     * 不影响 delivered 置位与图继续执行。
     */
    @Builder.Default
    private boolean deliveryWriteFailed = false;

    /**
     * 按画布节点 id 挂载的定稿 out（多为子流节点）。
     * 供占位符 {@code nodes.&lt;id&gt;.out…} 读取。
     */
    @Builder.Default
    private Map<String, Map<String, Object>> nodeOuts = new HashMap<>();

    /**
     * 子流 Run 指向父上下文；subflow-return 或收束时把 out 挂回父 nodeOuts。
     * 不参与暂停序列化。
     */
    private FlowRunContext parentContext;

    /**
     * 本子流在父图上对应的 subflow 节点 id，作为 nodeOuts 的键。
     */
    private String parentSubflowNodeId;

    /**
     * 开放 HTTP 写出端；场景/子流通常为 null。
     * 不参与暂停序列化，续跑须由入站层重新挂上。
     */
    private DeliverySink deliverySink;

    /**
     * 开放入站名额令牌；交付成功后由交付器 release 并清空本字段。
     */
    private String inboundSlotToken;

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
     * 当前 Run 是否允许执行外联 HTTP。
     * 场景/套件等登录入口：开跑时按操作者是否具备项目外联能力写入；
     * 开放 HTTP 入站：开跑时按项目「开放入站允许外联」开关写入；缺省 false。
     */
    @Builder.Default
    private boolean externalHttpPermitted = false;

    /**
     * 当前 Run 是否允许 Input 节点进入 paused、等人填表。
     * 场景等入口：开跑时按项目「允许暂停」写入，缺省 true；
     * 开放 HTTP 入站：开跑时固定 false（无人值守，不可等人）。
     * 为 false 时 Input 直接失败，不写 paused。
     * 子流 fork 时从父上下文原样继承。
     */
    @Builder.Default
    private boolean pausePermitted = true;

    /**
     * 本上下文内最近一次 HTTP 响应快照（status / headers / body / durationMs /
     * 实际响应形态 responseKind / 是否符合接口期望 expectedMatch）。
     * 中间非 HTTP 节点不会清空或改写本字段。
     */
    private HttpResponseSnapshot lastResponse;

    /**
     * 按画布节点 id 索引的 HTTP 响应快照。
     * 同一节点多次执行时覆盖为该节点最后一次响应；
     * 供路径 {@code nodes.<nodeId>.http…} 取值；暂停续跑时一并落盘，以免道具丢失。
     */
    @Builder.Default
    private Map<String, HttpResponseSnapshot> nodeResponses = new HashMap<>();

    /**
     * 当前 Run 所属测试项目 ID（子流归属校验）
     */
    private Long testProjectId;

    /**
     * 当前 Run 绑定的测试环境 ID，插件步骤据此定位要操作的环境；未绑定环境时为 null
     */
    private Long testProjectEnvId;

    /**
     * 当前测试流运行记录 ID，插件步骤调用时随请求带给插件，用于关联本次运行
     */
    private Long testFlowRunId;

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

    /**
     * 挂载某画布节点的定稿出参（拷贝写入）。
     * nodeId 或 payload 为空则忽略。
     */
    public void putNodeOut(String nodeId, Map<String, Object> outPayload) {
        if (nodeId == null || nodeId.isBlank() || outPayload == null) {
            return;
        }
        if (this.nodeOuts == null) {
            this.nodeOuts = new HashMap<>();
        }
        this.nodeOuts.put(nodeId.trim(), new LinkedHashMap<>(outPayload));
    }

    /**
     * 读取已挂载的节点出参 Map；未挂载或 id 空则 null。
     */
    public Map<String, Object> getNodeOut(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || this.nodeOuts == null) {
            return null;
        }
        return this.nodeOuts.get(nodeId.trim());
    }

    /**
     * 为子流组装子上下文：同一场 Run 内进入子图时调用。
     * <p>
     * 继承（同一身份与许可，避免子图丢环境/跑流 id 或权限变严变松）：
     * testProjectId、testProjectEnvId、testFlowRunId、projectAuthConfig、
     * externalHttpPermitted、pausePermitted；env / asset / session 与父共用同一 Map 引用。
     * 隔离：新建空 flow；in 只灌入本步 inputs 种子；subflowDepth = 父深度 + 1；
     * parentContext / parentSubflowNodeId 指向父，供子流 return 挂回父 nodeOuts。
     * parent 为空时仍能建最小子上下文（深度 1），仅供极端兜底。
     *
     * @param parent               父 Run 上下文
     * @param childSeed            子流 in 种子；可空
     * @param parentSubflowNodeId  父图上本 subflow 节点 id
     * @param childEntryNodeId     子图入口节点 id；可空
     * @param entryKind            子流入口种类 code（通常 subflow）
     * @param trigger              触发源 code（通常 subflow）
     * @return 子流上下文
     */
    public static FlowRunContext forkForSubflow(
            FlowRunContext parent,
            Map<String, Object> childSeed,
            String parentSubflowNodeId,
            String childEntryNodeId,
            String entryKind,
            String trigger) {
        Map<String, Object> seed = childSeed != null ? new HashMap<>(childSeed) : new HashMap<>();
        if (parent == null) {
            return FlowRunContext.builder()
                    .flow(new HashMap<>())
                    .in(new HashMap<>(seed))
                    .entryKind(entryKind)
                    .entryNodeId(childEntryNodeId)
                    .trigger(trigger)
                    .parentSubflowNodeId(parentSubflowNodeId)
                    .subflowDepth(1)
                    .build();
        }
        return FlowRunContext.builder()
                .testProjectId(parent.getTestProjectId())
                .testProjectEnvId(parent.getTestProjectEnvId())
                .testFlowRunId(parent.getTestFlowRunId())
                .projectAuthConfig(parent.getProjectAuthConfig())
                .env(parent.getEnv() != null ? parent.getEnv() : Map.of())
                .asset(parent.getAsset() != null ? parent.getAsset() : Map.of())
                .session(parent.getSession() != null ? parent.getSession() : new HashMap<>())
                .subflowDepth(parent.getSubflowDepth() + 1)
                .externalHttpPermitted(parent.isExternalHttpPermitted())
                .pausePermitted(parent.isPausePermitted())
                .flow(new HashMap<>())
                .in(new HashMap<>(seed))
                .entryKind(entryKind)
                .entryNodeId(childEntryNodeId)
                .trigger(trigger)
                .parentContext(parent)
                .parentSubflowNodeId(parentSubflowNodeId)
                .build();
    }
}
