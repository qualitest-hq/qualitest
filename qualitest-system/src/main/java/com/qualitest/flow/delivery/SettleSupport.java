package com.qualitest.flow.delivery;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.Map;

/**
 * 路径走尽（或失败后继续扫完）时的交付收束。
 * <p>
 * 若本次 Run 已在 {@code http-return} / {@code subflow-return} 交付过，此处不再写。
 * 开放 HTTP / 子流若整次从未执行返回节点，则读取入口节点 {@code data.fallbackOut}
 * （缺失则用 {@link #defaultFallback()}）补交一次，并返回「未执行到返回节点」错误，
 * 使 Outcome 记失败；子流场景下父图仍可读已挂上的兜底 out。
 * 场景主图不要求对外交付，本类直接无动作。
 */
public final class SettleSupport {

    /** 工具类，禁止实例化 */
    private SettleSupport() {
    }

    /**
     * 按需执行收束交付。
     *
     * @return 因缺少返回节点而补交时的失败错误；已交付或不需要兜底时为 null
     */
    public static StepError settle(FlowRunContext ctx, GraphJson graph, RunDelivery delivery) {
        if (ctx == null || delivery == null) {
            return null;
        }
        if (ctx.isDelivered()) {
            return null;
        }
        EntryKind kind = EntryKind.fromCode(ctx.getEntryKind());
        if (!kind.requiresFallbackOut()) {
            return null;
        }
        OutPayload fallback = resolveFallbackOut(ctx, graph);
        if (fallback == null) {
            fallback = defaultFallback();
        }
        delivery.deliver(ctx, fallback, false);
        return StepError.of(FlowErrorCode.TF_RETURN_MISSING, "未执行到返回节点，已用入口兜底出参交付");
    }

    /**
     * 从本图入口节点 {@code data.fallbackOut} 读取兜底包；无入口或值不是 Map 则 null。
     */
    @SuppressWarnings("unchecked")
    public static OutPayload resolveFallbackOut(FlowRunContext ctx, GraphJson graph) {
        GraphNode entry = findEntryNode(ctx, graph);
        if (entry == null || entry.getData() == null) {
            return null;
        }
        Object raw = entry.getData().get("fallbackOut");
        if (raw instanceof Map<?, ?> m) {
            return OutPayload.fromMap((Map<String, Object>) m);
        }
        return null;
    }

    /**
     * 定位本 Run 使用的入口节点：优先 {@code ctx.entryNodeId}；否则取图上第一个入口类型节点。
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
        }
        for (GraphNode n : graph.getNodes()) {
            if (n != null && FlowNodeType.isEntry(n.getType())) {
                return n;
            }
        }
        return null;
    }

    /**
     * 入口未配置 fallbackOut 时的默认兜底包：HTTP 500，body 含 code=NO_RETURN。
     */
    public static OutPayload defaultFallback() {
        OutPayload p = new OutPayload();
        p.setStatus(500);
        p.getHeaders().put("Content-Type", RunDelivery.DEFAULT_CONTENT_TYPE);
        p.setBody(Map.of(
                "code", "NO_RETURN",
                "message", "未执行到返回节点"));
        return p;
    }
}
