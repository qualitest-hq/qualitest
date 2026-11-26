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
 * 两条交付通道，语义不同：
 * <ul>
 *   <li>{@link #deliverHttp} — 开放 HTTP：补缺省 status / Content-Type 后经 DeliverySink 写出，
 *       ctx.out 规范成 status/headers/body；写出失败只置 {@code deliveryWriteFailed}</li>
 *   <li>{@link #deliverSubflow} — 子流：把 {@code outPackage} 原样（整包）写入 ctx.out 并挂到父上下文
 *       对应 subflow 节点的 {@code out}，不做 status/headers/body 拆解</li>
 * </ul>
 * 两者都支持两种调用：返回节点（{@code fromReturn=true}，计入 {@code returnCount}，整次 Run 至多一次）
 * 与路径走尽的收束兜底（{@code fromReturn=false}，已交付则跳过）。场景不对外交付，不调用本类。
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
     * 开放 HTTP 交付：缺省值补齐 → 写入 ctx.out（status/headers/body）→ DeliverySink 写出。
     *
     * @param ctx        运行上下文
     * @param payload    定稿出参；null 当作空包
     * @param fromReturn true 表示来自 {@code http-return}，需占用 returnCount
     */
    public void deliverHttp(FlowRunContext ctx, OutPayload payload, boolean fromReturn) {
        if (ctx == null || !beginDelivery(ctx, fromReturn)) {
            return;
        }
        OutPayload toWrite = payload != null ? payload.copy() : new OutPayload();
        normalizeHttpDefaults(toWrite);
        applyToOutMap(ctx, toWrite);
        DeliverySink sink = ctx.getDeliverySink();
        if (sink != null && !sink.complete(toWrite)) {
            ctx.setDeliveryWriteFailed(true);
        }
        finishDelivery(ctx);
    }

    /**
     * 子流交付：把定稿出参整包写入 ctx.out，并原样挂到父上下文对应 subflow 节点的 {@code out}。
     * 不做 status/headers/body 拆解——业务对象（如 {@code {code,data}}）原样挂父。
     *
     * @param ctx        子流运行上下文
     * @param outPackage 定稿出参整包；null 当作空包
     * @param fromReturn true 表示来自 {@code subflow-return}，需占用 returnCount
     */
    public void deliverSubflow(FlowRunContext ctx, Map<String, Object> outPackage, boolean fromReturn) {
        if (ctx == null || !beginDelivery(ctx, fromReturn)) {
            return;
        }
        Map<String, Object> packageCopy = outPackage != null
                ? new LinkedHashMap<>(outPackage)
                : new LinkedHashMap<>();
        applyPackageToOut(ctx, packageCopy);
        String parentNodeId = ctx.getParentSubflowNodeId();
        FlowRunContext parent = ctx.getParentContext();
        if (parent != null && parentNodeId != null && !parentNodeId.isBlank()) {
            parent.putNodeOut(parentNodeId, packageCopy);
        }
        finishDelivery(ctx);
    }

    /**
     * 交付前置：来自返回节点时判重并计数；收束兜底时已交付则跳过。
     *
     * @return true 表示可以继续交付；false 表示已交付、无需再写
     */
    private boolean beginDelivery(FlowRunContext ctx, boolean fromReturn) {
        if (fromReturn) {
            // 先判次数 / 已交付，避免第二次 return 静默跳过
            if (ctx.getReturnCount() >= 1 || ctx.isDelivered()) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_RETURN_DUPLICATE,
                        "整次 Run 至多执行一次返回节点");
            }
            ctx.setReturnCount(ctx.getReturnCount() + 1);
            return true;
        }
        return !ctx.isDelivered();
    }

    /** 交付后置：置 delivered 并释放入站名额 */
    private void finishDelivery(FlowRunContext ctx) {
        ctx.setDelivered(true);
        releaseInboundSlot(ctx);
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

    /** 整包写入运行时 out（子流用，不拆 status/headers/body） */
    private static void applyPackageToOut(FlowRunContext ctx, Map<String, Object> outPackage) {
        Map<String, Object> out = ctx.getOut();
        if (out == null) {
            out = new LinkedHashMap<>();
            ctx.setOut(out);
        }
        out.clear();
        out.putAll(outPackage);
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
