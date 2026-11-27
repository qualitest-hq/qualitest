package com.qualitest.flow.delivery;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.model.FlowNodeType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路径走尽（或失败后继续扫完）时的交付收束。
 * <p>
 * 若本次 Run 已在 {@code http-return} / {@code subflow-return} 交付过，此处不再写。
 * 开放 HTTP / 子流若整次从未执行返回节点，则读取入口节点 {@code data.fallbackOut}
 * 补交一次（HTTP 走完整 out 包；子流按业务对象包成 {@code {body: …}} 后整包挂父），
 * 并返回「未执行到返回节点」错误，使 Outcome 记失败；子流场景下父图仍可读已挂上的兜底 out。
 * 场景主图不要求对外交付，本类直接无动作。
 */
public final class SettleSupport {

    /** 工具类，禁止实例化 */
    private SettleSupport() {
    }

    /**
     * 按需执行收束交付。
     *
     * @return 因缺少返回节点而补交时的失败错误；已交付但写出失败时的错误；
     *         已交付成功或不需要兜底时为 null
     */
    public static StepError settle(FlowRunContext ctx, GraphJson graph, RunDelivery delivery) {
        if (ctx == null || delivery == null) {
            return null;
        }
        if (ctx.isDelivered()) {
            // 返回节点已交付但写出失败：收束不再写第二遍，仍须让 Run 记失败
            if (ctx.isDeliveryWriteFailed()) {
                return StepError.of(FlowErrorCode.TF_DELIVERY_WRITE_FAILED, "开放响应写出失败");
            }
            return null;
        }
        EntryKind kind = EntryKind.fromCode(ctx.getEntryKind());
        if (!kind.requiresFallbackOut()) {
            return null;
        }
        if (kind == EntryKind.HTTP) {
            OutPayload fallback = resolveFallbackOut(ctx, graph);
            if (fallback == null) {
                fallback = defaultFallback();
            }
            delivery.deliverHttp(ctx, fallback, false);
            if (ctx.isDeliveryWriteFailed()) {
                return StepError.of(FlowErrorCode.TF_DELIVERY_WRITE_FAILED,
                        "未执行到返回节点，且开放响应写出失败");
            }
            return StepError.of(FlowErrorCode.TF_RETURN_MISSING, "未执行到返回节点，已用入口兜底出参交付");
        }
        // 子流：整包挂父（业务对象规范成 {body: …}；存量完整 out 形照挂）
        delivery.deliverSubflow(ctx, resolveSubflowFallbackPackage(ctx, graph), false);
        return StepError.of(FlowErrorCode.TF_RETURN_MISSING, "未执行到返回节点，已用入口兜底出参交付");
    }

    /**
     * 子流兜底出参的挂父形态：
     * <ul>
     *   <li>入口 {@code fallbackOut} 是业务对象（无 status/headers/body 完整形）→ {@code {body: <该对象>}}</li>
     *   <li>存量完整 out 形（含可解析 status + body 键）→ 原样整包挂</li>
     *   <li>未配置 → {@code {body: {code: NO_RETURN, message: 未执行到返回节点}}}</li>
     * </ul>
     */
    public static Map<String, Object> resolveSubflowFallbackPackage(FlowRunContext ctx, GraphJson graph) {
        Map<String, Object> raw = rawFallbackMap(ctx, graph);
        if (raw == null) {
            return defaultSubflowFallbackPackage();
        }
        Map<String, Object> packageMap = new LinkedHashMap<>();
        if (isFullOutShape(raw)) {
            packageMap.putAll(raw);
            return packageMap;
        }
        packageMap.put("body", new LinkedHashMap<>(raw));
        return packageMap;
    }

    /** 子流图不可用 / 未配置入口兜底时的默认挂父包：{@code {body:{code:NO_RETURN,…}}} */
    public static Map<String, Object> defaultSubflowFallbackPackage() {
        Map<String, Object> packageMap = new LinkedHashMap<>();
        packageMap.put("body", defaultFallbackBody());
        return packageMap;
    }

    /**
     * 从本图入口节点 {@code data.fallbackOut} 读取兜底包，解析成 HTTP 用 {@link OutPayload}。
     * 两种落盘形态：
     * <ul>
     *   <li>完整出参：含 status / headers / body 任一键 → 按完整 OutPayload 解析（HTTP 入口常用）。</li>
     *   <li>业务对象：不含上述键 → 整包放进 {@code out.body} 再交付。</li>
     * </ul>
     */
    public static OutPayload resolveFallbackOut(FlowRunContext ctx, GraphJson graph) {
        Map<String, Object> map = rawFallbackMap(ctx, graph);
        if (map == null) {
            return null;
        }
        if (isFullOutShape(map)) {
            return OutPayload.fromMap(map);
        }
        // 业务对象兜底：整包作为 body，便于父侧读 nodes.<id>.out.body
        OutPayload p = new OutPayload();
        p.setBody(new LinkedHashMap<>(map));
        return p;
    }

    /** 读入口 {@code data.fallbackOut} 原始 Map；无入口或不是 Map 时 null */
    private static Map<String, Object> rawFallbackMap(FlowRunContext ctx, GraphJson graph) {
        GraphNode entry = findEntryNode(ctx, graph);
        if (entry == null || entry.getData() == null) {
            return null;
        }
        Object raw = entry.getData().get("fallbackOut");
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return map;
    }

    /**
     * 判断 Map 是否已是完整出参形。
     * 须同时具备可解析的 HTTP status 与 {@code body} 键，避免子流业务对象里的数值
     * {@code status}（如业务码 200）被误判为完整 out 而不再包进 body。
     * 旧子流图若仍存完整形（含 status + body），走此分支照挂。
     */
    private static boolean isFullOutShape(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        if (HttpStatusCodes.coerce(map.get("status")) == null) {
            return false;
        }
        return map.containsKey("body");
    }

    /**
     * 定位本 Run 使用的入口节点。
     * 优先用 {@code ctx.entryNodeId}；未指定时图上恰好一个入口类型节点则用之，多个则返回 null。
     */
    public static GraphNode findEntryNode(FlowRunContext ctx, GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return null;
        }
        String entryId = ctx != null ? ctx.getEntryNodeId() : null;
        if (entryId != null && !entryId.isBlank()) {
            for (GraphNode n : graph.getNodes()) {
                if (n != null && entryId.equals(n.getId())) {
                    return n;
                }
            }
            return null;
        }
        GraphNode sole = null;
        for (GraphNode n : graph.getNodes()) {
            if (n == null || !FlowNodeType.isEntry(n.getType())) {
                continue;
            }
            if (sole != null) {
                return null;
            }
            sole = n;
        }
        return sole;
    }

    /**
     * 入口未配置 fallbackOut 时的默认兜底包：HTTP 500，body 含 code=NO_RETURN。
     */
    public static OutPayload defaultFallback() {
        OutPayload p = new OutPayload();
        p.setStatus(500);
        p.getHeaders().put("Content-Type", RunDelivery.DEFAULT_CONTENT_TYPE);
        p.setBody(defaultFallbackBody());
        return p;
    }

    /** 默认兜底业务对象（HTTP 的 body / 子流包进 body 用） */
    private static Map<String, Object> defaultFallbackBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "NO_RETURN");
        body.put("message", "未执行到返回节点");
        return body;
    }
}
