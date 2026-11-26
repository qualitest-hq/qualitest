package com.qualitest.flow.node.impl;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.HttpStatusCodes;
import com.qualitest.flow.delivery.OutPayload;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandler;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 返回节点执行器，支持 {@code http-return} 与 {@code subflow-return}。
 * <p>
 * 按 {@code data.from} 占位符路径解析 Map，整表晋升写入 {@code ctx.out} 后定稿交付：
 * <ul>
 *   <li>{@code from} 必填（保存期缺省由 {@code flow.out} 等规则写入）；运行时缺失或解析结果非 Map 则失败</li>
 *   <li>{@code http-return} — 再写入 {@code data.status}（缺省 200，须 100–599）到 {@code out.status}，经 DeliverySink 写出</li>
 *   <li>{@code subflow-return} — 把晋升后的 out <b>整包</b>挂到父图对应 subflow 节点的 out（不拆 status/headers/body）</li>
 * </ul>
 * 交付后本步记通过，图继续沿出边前进；整次 Run 第二次执行任一返回节点会失败（{@link FlowErrorCode#TF_RETURN_DUPLICATE}）。
 */
@Component
public class ReturnNodeHandler implements NodeHandler {

    private final RunDelivery runDelivery;
    private final PlaceholderResolver placeholderResolver;

    /** 注入交付器与占位符解析器 */
    public ReturnNodeHandler(RunDelivery runDelivery, PlaceholderResolver placeholderResolver) {
        this.runDelivery = runDelivery;
        this.placeholderResolver = placeholderResolver;
    }

    /** 匹配 http-return 与 subflow-return */
    @Override
    public boolean supports(String type) {
        return FlowNodeType.isReturn(type);
    }

    /** 解析 from、晋升 out、按种类交付后返回本步结果 */
    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        String nodeType = node != null ? node.getType() : null;
        String nodeName = AbstractStubNodeHandler.resolveNodeName(node);
        Map<String, Object> data = node != null && node.getData() != null ? node.getData() : Map.of();

        try {
            // 第二次 return：在改写 ctx.out 之前失败，保留已交付的 out 快照给后续步骤读
            if (ctx != null && (ctx.getReturnCount() >= 1 || ctx.isDelivered())) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_RETURN_DUPLICATE,
                        "整次 Run 至多执行一次返回节点");
            }
            String from = requireFrom(data);
            Object resolved = placeholderResolver.resolvePathSegment(ctx, from);
            Map<String, Object> promoted = requireMap(resolved, from);
            Map<String, Object> out = ensureOut(ctx);
            out.clear();
            out.putAll(promoted);

            if (FlowNodeType.HTTP_RETURN.matches(nodeType)) {
                out.put("status", resolveHttpReturnStatus(data));
            }

            EntryKind mode = resolveMode(nodeType, ctx);
            if (mode == EntryKind.HTTP) {
                runDelivery.deliverHttp(ctx, OutPayload.fromMap(ctx.getOut()), true);
            } else {
                // 子流：晋升后的 out 整包挂父，不拆 status/headers/body
                runDelivery.deliverSubflow(ctx, ctx.getOut(), true);
            }
            return result(node, nodeType, incomingEdgeId, nodeName, t0, ctx, RunStatus.PASSED.getCode(), null);
        } catch (FlowExecutionException e) {
            return result(node, nodeType, incomingEdgeId, nodeName, t0, ctx, RunStatus.FAILED.getCode(),
                    StepError.of(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            return result(node, nodeType, incomingEdgeId, nodeName, t0, ctx, RunStatus.FAILED.getCode(),
                    StepError.of(FlowErrorCode.TF_STEP_ERROR,
                            e.getMessage() != null ? e.getMessage() : "返回节点异常"));
        }
    }

    /** 读取并校验 data.from；空则抛错 */
    private static String requireFrom(Map<String, Object> data) {
        Object fromObj = data != null ? data.get("from") : null;
        String from = fromObj == null ? "" : String.valueOf(fromObj).trim();
        if (from.isEmpty()) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR, "返回节点缺少 data.from");
        }
        return from;
    }

    /**
     * 将占位符解析结果转为可写入 out 的 Map；非 Map/JSONObject 则抛错。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object resolved, String from) {
        if (resolved instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return copy;
        }
        if (resolved instanceof JSONObject jo) {
            return new LinkedHashMap<>(jo);
        }
        throw new FlowExecutionException(
                FlowErrorCode.TF_STEP_ERROR,
                "返回节点 from=\"" + from + "\" 须解析为对象 Map");
    }

    /** 确保 ctx.out 非 null，可直接 clear / putAll */
    private static Map<String, Object> ensureOut(FlowRunContext ctx) {
        Map<String, Object> out = ctx != null ? ctx.getOut() : null;
        if (out == null) {
            out = new LinkedHashMap<>();
            if (ctx != null) {
                ctx.setOut(out);
            }
        }
        return out;
    }

    /**
     * http-return 写出用状态码；缺省 200，须为 100–599 整数。
     */
    private static Integer resolveHttpReturnStatus(Map<String, Object> data) {
        Object raw = data != null ? data.get("status") : null;
        if (raw == null) {
            return HttpStatusCodes.DEFAULT;
        }
        Integer status = HttpStatusCodes.coerce(raw);
        if (status == null) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_STEP_ERROR, "http-return status 须为 100–599 的整数");
        }
        return status;
    }

    /**
     * 由返回节点 type 得到应付交付的 EntryKind，并核对上下文 entryKind；
     * type 无法识别或与当前 Run 入口种类不符时抛错。
     */
    private static EntryKind resolveMode(String nodeType, FlowRunContext ctx) {
        EntryKind required = EntryKind.forReturnNodeType(nodeType);
        if (required == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_NODE_UNSUPPORTED, "不支持的返回节点类型: " + nodeType);
        }
        EntryKind actual = EntryKind.fromCode(ctx != null ? ctx.getEntryKind() : null);
        if (actual != required) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_STEP_ERROR,
                    nodeType + " 仅用于 entryKind=" + required.getCode()
                            + "（当前 entryKind=" + actual.getCode() + "）");
        }
        return required;
    }

    /** 组装本步结果；flowAfter 为交付后的 flow 变量快照 */
    private static StepResult result(GraphNode node, String nodeType, String incomingEdgeId, String nodeName,
                                     long t0, FlowRunContext ctx, String status, StepError error) {
        return StepResult.builder()
                .nodeId(node != null ? node.getId() : null)
                .nodeType(nodeType)
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(status)
                .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                .flowAfter(AbstractStubNodeHandler.copyFlow(ctx))
                .error(error)
                .build();
    }
}