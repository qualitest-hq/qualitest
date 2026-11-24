package com.qualitest.flow.delivery;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 立刻交付定稿出参，并在交付成功后释放开放入站名额令牌。
 * <p>
 * 两种调用：
 * <ul>
 *   <li>返回节点（{@code http-return} / {@code subflow-return}）：{@code fromReturn=true}，
 *       计入 {@code returnCount}，整次 Run 至多成功交付一次</li>
 *   <li>路径走尽收束：{@code fromReturn=false}，用入口兜底包补交（仅开放/子流）</li>
 * </ul>
 * 上下文已 {@code delivered} 则直接返回，不再写第二遍。
 * <ul>
 *   <li>{@link EntryKind#HTTP} — 补默认 status/Content-Type → 写入 ctx.out → DeliverySink 写出；
 *       写出失败只置 {@code deliveryWriteFailed}</li>
 *   <li>{@link EntryKind#SUBFLOW} — 写入子 ctx.out，并挂到父上下文对应 subflow 节点的 out</li>
 *   <li>{@link EntryKind#SCENARIO} — 不对外交付，本类直接忽略</li>
 * </ul>
 */
@Component
public class RunDelivery {

    /** 开放 HTTP 写出时，headers 缺 Content-Type 所补的默认值 */
    public static final String DEFAULT_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final InboundSlotRegistry inboundSlotRegistry;

    /** 注入入站名额注册表，交付成功后释放令牌 */
    public RunDelivery(InboundSlotRegistry inboundSlotRegistry) {
        this.inboundSlotRegistry = inboundSlotRegistry;
    }

    /**
     * 收束用：按上下文 {@code entryKind} 决定是否、以及如何交付。
     * 场景或非对外种类直接返回；开放/子流再转入带 mode 的重载。
     */
    public void deliver(FlowRunContext ctx, OutPayload payload, boolean fromReturn) {
        if (ctx == null) {
            return;
        }
        EntryKind kind = EntryKind.fromCode(ctx.getEntryKind());
        if (!kind.deliversExternally()) {
            return;
        }
        deliver(ctx, payload, fromReturn, kind);
    }

    /**
     * 按指定种类执行一次交付。
     *
     * @param ctx        运行上下文
     * @param payload    定稿出参；null 当作空包
     * @param fromReturn true 表示来自返回节点，需占用 returnCount
     * @param mode       仅 {@link EntryKind#HTTP} / {@link EntryKind#SUBFLOW} 有效；其它忽略
     */
    public void deliver(FlowRunContext ctx, OutPayload payload, boolean fromReturn, EntryKind mode) {
        if (ctx == null || mode == null || !mode.deliversExternally()) {
            return;
        }
        // 来自返回节点：先判次数 / 已交付，避免第二次 return 静默跳过
        if (fromReturn) {
            if (ctx.getReturnCount() >= 1 || ctx.isDelivered()) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_RETURN_DUPLICATE,
                        "整次 Run 至多执行一次返回节点");
            }
            ctx.setReturnCount(ctx.getReturnCount() + 1);
        } else if (ctx.isDelivered()) {
            return;
        }

        OutPayload toWrite = payload != null ? payload.copy() : new OutPayload();
        switch (mode) {
            case HTTP -> deliverHttp(ctx, toWrite);
            case SUBFLOW -> deliverSubflow(ctx, toWrite);
            case SCENARIO -> {
                // deliversExternally 已拦下，不会进入
            }
        }
        ctx.setDelivered(true);
        releaseInboundSlot(ctx);
    }

    /** HTTP：规范化默认值 → 覆盖 ctx.out → sink.complete（无 sink 则只写 out） */
    private void deliverHttp(FlowRunContext ctx, OutPayload payload) {
        normalizeHttpDefaults(payload);
        applyToOutMap(ctx, payload);
        DeliverySink sink = ctx.getDeliverySink();
        if (sink == null) {
            return;
        }
        boolean ok = sink.complete(payload);
        if (!ok) {
            ctx.setDeliveryWriteFailed(true);
        }
    }

    /** 子流：覆盖子 ctx.out，并把整包挂到父图 parentSubflowNodeId 对应节点 */
    private void deliverSubflow(FlowRunContext ctx, OutPayload payload) {
        applyToOutMap(ctx, payload);
        String parentNodeId = ctx.getParentSubflowNodeId();
        FlowRunContext parent = ctx.getParentContext();
        if (parent != null && parentNodeId != null && !parentNodeId.isBlank()) {
            parent.putNodeOut(parentNodeId, payload.toMap());
        }
    }

    /**
     * HTTP 写出缺省：status 为空则 200；headers 中无 Content-Type（大小写不敏感）则补默认 JSON。
     */
    public static void normalizeHttpDefaults(OutPayload payload) {
        if (payload.getStatus() == null) {
            payload.setStatus(200);
        }
        Map<String, Object> headers = payload.getHeaders();
        if (headers == null) {
            headers = new LinkedHashMap<>();
            payload.setHeaders(headers);
        }
        if (!hasContentType(headers)) {
            headers.put("Content-Type", DEFAULT_CONTENT_TYPE);
        }
    }

    private static boolean hasContentType(Map<String, Object> headers) {
        for (String key : headers.keySet()) {
            if (key != null && "content-type".equals(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 清空并写入定稿到运行时 out，避免残留未交付字段 */
    private static void applyToOutMap(FlowRunContext ctx, OutPayload payload) {
        Map<String, Object> out = ctx.getOut();
        if (out == null) {
            out = new LinkedHashMap<>();
            ctx.setOut(out);
        }
        out.clear();
        out.putAll(payload.toMap());
    }

    /** 若上下文持有入站名额令牌则释放并清空；无令牌则忽略 */
    private void releaseInboundSlot(FlowRunContext ctx) {
        String token = ctx.getInboundSlotToken();
        if (token != null && !token.isBlank()) {
            inboundSlotRegistry.release(token);
            ctx.setInboundSlotToken(null);
        }
    }
}
