package com.qualitest.flow.node;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AssertNodeHandler;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import com.qualitest.flow.node.impl.DelayNodeHandler;
import com.qualitest.flow.node.impl.HttpNodeHandler;
import com.qualitest.flow.node.impl.ScriptNodeHandler;
import com.qualitest.flow.node.impl.SubflowNodeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 节点 Handler 注册表：按 {@code nodes[].type} 路由到对应 {@link NodeHandler} 实现。
 * <p>
 * 当前支持 http、assert、delay、condition、assign、script、subflow。
 */
@Component
public class NodeHandlerRegistry {

    /** 已注册的 Handler 列表，按 type 匹配第一个 supports 为真的实现 */
    private final List<NodeHandler> handlers;

    @Autowired
    public NodeHandlerRegistry(HttpNodeHandler httpNodeHandler,
                               AssertNodeHandler assertNodeHandler,
                               DelayNodeHandler delayNodeHandler,
                               ConditionNodeHandler conditionNodeHandler,
                               AssignNodeHandler assignNodeHandler,
                               ScriptNodeHandler scriptNodeHandler,
                               SubflowNodeHandler subflowNodeHandler) {
        this.handlers = List.of(
                httpNodeHandler,
                assertNodeHandler,
                delayNodeHandler,
                conditionNodeHandler,
                assignNodeHandler,
                scriptNodeHandler,
                subflowNodeHandler
        );
    }

    /**
     * 测试专用：注入自定义 Handler 列表（非 Spring Bean 构造路径）。
     */
    public NodeHandlerRegistry(List<NodeHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    /**
     * 按 type 查找 Handler；未注册时抛 TF_NODE_UNSUPPORTED。
     */
    public NodeHandler requireHandler(String type) {
        if (type == null || type.isBlank()) {
            throw unsupported(type);
        }
        return handlers.stream()
                .filter(handler -> handler.supports(type))
                .findFirst()
                .orElseThrow(() -> unsupported(type));
    }

    /**
     * 执行单步：根据 node.type 路由 Handler 并返回 StepResult。
     */
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        NodeHandler handler = requireHandler(node.getType());
        return handler.execute(ctx, node, incomingEdgeId);
    }

    private static FlowExecutionException unsupported(String type) {
        String display = type == null || type.isBlank() ? "(空)" : type;
        return new FlowExecutionException(
                FlowErrorCode.TF_NODE_UNSUPPORTED,
                "不支持的节点类型: " + display
        );
    }
}
